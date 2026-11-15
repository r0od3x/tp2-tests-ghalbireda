package ma.emsi.ghalbireda.tp2.tests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Outils mis a la disposition du LLM pour le test 7.
 * <p>
 * Un LLM n'a aucune connaissance en temps reel : il ne peut pas savoir le temps
 * qu'il fera demain, comme on l'a deja constate au test 1 avec l'heure. Un outil
 * comble ce manque : c'est une simple methode Java annotee par {@code @Tool}.
 * <p>
 * Le mecanisme est le suivant. LangChain4j envoie au LLM, en meme temps que la
 * question, la description de chaque outil disponible (son nom, le texte de
 * l'annotation et la description de ses parametres). Si le LLM estime qu'il a
 * besoin d'un outil, il ne repond pas a l'utilisateur : il repond a
 * l'application en demandant l'appel de la methode, avec les valeurs des
 * parametres. LangChain4j execute la methode, renvoie son resultat au LLM, et
 * c'est seulement alors que le LLM redige sa reponse. C'est donc toujours
 * l'application qui execute le code, jamais le LLM.
 * <p>
 * Le texte de l'annotation est donc tout sauf decoratif : c'est la seule chose
 * sur laquelle le LLM s'appuie pour decider s'il doit utiliser l'outil et
 * comment le remplir. Une description vague conduit a un outil ignore ou mal
 * appele.
 * <p>
 * Les deux outils utilisent les APIs d'Open-Meteo, qui sont gratuites et ne
 * demandent pas de cle.
 */
public class MeteoTool {

    private static final String URL_PREVISIONS = "https://api.open-meteo.com/v1/forecast";

    private static final String URL_GEOCODAGE = "https://geocoding-api.open-meteo.com/v1/search";

    /**
     * Probabilite de precipitations, en pourcentage, a partir de laquelle on
     * considere que la pluie est probable. Modifier cette valeur permet de
     * tester le bon fonctionnement de la methode sans attendre qu'il pleuve.
     */
    private int seuilPluie = 50;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    public int getSeuilPluie() {
        return seuilPluie;
    }

    public void setSeuilPluie(int seuilPluie) {
        this.seuilPluie = seuilPluie;
    }

    /**
     * Outil 1 : previsions de precipitations pour un lieu donne par ses
     * coordonnees.
     *
     * @param latitude    latitude du lieu.
     * @param longitude   longitude du lieu.
     * @param nombreJours nombre de jours de prevision.
     * @return un texte decrivant les previsions, destine au LLM.
     */
    @Tool("""
            Donne les previsions de precipitations d'un lieu, identifie par sa latitude \
            et sa longitude, pour les prochains jours (jour courant compris). \
            Pour chaque jour, retourne la date, la probabilite maximale de precipitations \
            en pourcentage, et si la pluie est probable ou non. \
            A utiliser pour toute question sur la pluie, les precipitations ou l'utilite \
            d'un parapluie. Les coordonnees sont obligatoires : si l'utilisateur donne un \
            nom de ville, obtenir d'abord ses coordonnees avec l'outil de geolocalisation.""")
    public String isPluiePrevue(
            @P("latitude du lieu, en degres decimaux (par exemple 48.85 pour Paris)")
            double latitude,
            @P("longitude du lieu, en degres decimaux (par exemple 2.35 pour Paris)")
            double longitude,
            @P("nombre de jours de prevision, jour courant compris, entre 1 et 16")
            int nombreJours) {

        String url = URL_PREVISIONS
                + "?latitude=" + latitude
                + "&longitude=" + longitude
                + "&daily=precipitation_probability_max"
                + "&forecast_days=" + nombreJours;

        try {
            JsonNode racine = appelerApi(url);
            JsonNode jours = racine.path("daily").path("time");
            JsonNode probabilites = racine.path("daily").path("precipitation_probability_max");

            if (jours.isMissingNode() || !jours.isArray() || jours.isEmpty()) {
                return "Previsions indisponibles pour ces coordonnees.";
            }

            StringBuilder resultat = new StringBuilder();
            resultat.append("Previsions de precipitations (seuil de pluie probable : ")
                    .append(seuilPluie).append("%) :\n");

            for (int i = 0; i < jours.size(); i++) {
                int probabilite = probabilites.get(i).asInt();
                resultat.append("- ").append(jours.get(i).asText())
                        .append(" : ").append(probabilite).append("% de chances de pluie, ")
                        .append(probabilite >= seuilPluie ? "pluie probable" : "pluie peu probable")
                        .append("\n");
            }
            return resultat.toString();

        } catch (Exception e) {
            // Le message d'erreur est renvoye au LLM, qui pourra l'expliquer a
            // l'utilisateur au lieu de laisser l'application planter.
            return "Impossible d'obtenir les previsions : " + e.getMessage();
        }
    }

    /**
     * Outil 2 : coordonnees d'une ville a partir de son nom.
     * <p>
     * S'il existe plusieurs villes de ce nom dans le monde, la premiere de la
     * liste est retenue. Pour lever l'ambiguite, il suffit de preciser le pays.
     *
     * @param ville nom de la ville, en anglais.
     * @return un tableau de deux valeurs : la latitude et la longitude.
     */
    @Tool("""
            Donne la latitude et la longitude d'une ville a partir de son nom. \
            Retourne un tableau de deux nombres : la latitude puis la longitude. \
            A utiliser des que l'utilisateur designe un lieu par son nom plutot que \
            par ses coordonnees.""")
    public double[] coordonnees(
            @P("nom de la ville en anglais, eventuellement suivi du pays separe par une "
                    + "virgule pour lever une ambiguite, par exemple \"Rabat,Morocco\"")
            String ville) {

        String url = URL_GEOCODAGE
                + "?name=" + URLEncoder.encode(ville, StandardCharsets.UTF_8)
                + "&count=1&language=en&format=json";

        try {
            JsonNode racine = appelerApi(url);
            JsonNode resultats = racine.path("results");

            if (!resultats.isArray() || resultats.isEmpty()) {
                // Ville inconnue : on leve une exception, LangChain4j transmet
                // le message au LLM qui pourra le dire a l'utilisateur.
                throw new IllegalArgumentException("Ville inconnue : " + ville);
            }

            JsonNode premiere = resultats.get(0);
            double latitude = premiere.path("latitude").asDouble();
            double longitude = premiere.path("longitude").asDouble();
            return new double[]{latitude, longitude};

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Geolocalisation impossible : " + e.getMessage(), e);
        }
    }

    /**
     * Envoie une requete GET et retourne le document JSON de la reponse.
     *
     * @param url l'URL a appeler.
     * @return la racine du document JSON.
     * @throws Exception en cas d'erreur reseau ou de reponse non JSON.
     */
    private JsonNode appelerApi(String url) throws Exception {
        HttpRequest requete = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        HttpResponse<String> reponse =
                client.send(requete, HttpResponse.BodyHandlers.ofString());

        if (reponse.statusCode() != 200) {
            throw new RuntimeException("Code HTTP " + reponse.statusCode());
        }
        return mapper.readTree(reponse.body());
    }
}
