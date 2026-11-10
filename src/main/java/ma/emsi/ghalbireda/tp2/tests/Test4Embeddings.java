package ma.emsi.ghalbireda.tp2.tests;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.CosineSimilarity;

/**
 * Test 4 du TP 2 : embeddings et similarite cosinus.
 * <p>
 * Un embedding est un vecteur de nombres qui represente le sens d'un texte. Deux
 * textes de sens proche ont des vecteurs qui pointent dans des directions
 * proches ; la similarite cosinus mesure l'angle entre ces deux vecteurs. En
 * theorie elle va de -1 a 1, en pratique les modeles courants donnent plutot des
 * valeurs entre 0,5 et 1.
 * <p>
 * Ce qui compte n'est pas la valeur absolue de la similarite, mais l'ordre :
 * deux phrases similaires doivent obtenir un score plus eleve que deux phrases
 * qui n'ont rien a voir. C'est cet ordre que le RAG utilise pour choisir les
 * morceaux de documents les plus pertinents pour une question (tests 5 et 6).
 * <p>
 * Deux modeles sont compares sur les memes couples de phrases :
 * <ul>
 *   <li>BgeSmallEnV15Quantized : modele integre, execute en local, gratuit, mais
 *       entraine sur de l'anglais ; il se comporte mal sur du francais ;</li>
 *   <li>text-embedding-3-small : modele externe d'OpenAI, multilingue, qui coute
 *       des tokens (mais tres peu : les embeddings sont bien moins chers que la
 *       generation de texte).</li>
 * </ul>
 */
public class Test4Embeddings {

    private static final String NOM_MODELE_EMBEDDINGS = "text-embedding-3-small";

    /**
     * Couples de phrases a comparer. Pour chaque couple : un libelle qui dit ce
     * que le couple est censé illustrer, puis les deux phrases.
     */
    private static final String[][] COUPLES = {
            {
                    "1. Deux phrases similaires (meme sens, mots differents)",
                    "Le chat dort sur le canape.",
                    "Un felin se repose sur le sofa."
            },
            {
                    "2. Deux phrases opposees (meme sujet, avis contraire)",
                    "J'adore ce film, c'est un chef d'oeuvre.",
                    "Je deteste ce film, c'est une catastrophe."
            },
            {
                    "3. Deux phrases sans aucun rapport",
                    "La capitale de la France est Paris.",
                    "Il faut trois oeufs pour cette recette de gateau."
            },
            {
                    "4. Meme question reformulee (cas typique du RAG)",
                    "Comment reinitialiser mon mot de passe ?",
                    "J'ai oublie mon mot de passe, comment faire pour le changer ?"
            },
            {
                    "5. Phrases presque identiques mais de sens different",
                    "Le train part a 8 heures.",
                    "Le train arrive a 8 heures."
            },
            {
                    "6. Deux phrases identiques (similarite maximale attendue)",
                    "Le nom du chat de Pierre est Felix.",
                    "Le nom du chat de Pierre est Felix."
            },
            {
                    "7. Une phrase et sa traduction en anglais",
                    "Le chat de Pierre dort sur le canape.",
                    "Pierre's cat is sleeping on the couch."
            }
    };

    public static void main(String[] args) {
        String cleApi = System.getenv("OPENAI_API_KEY");
        if (cleApi == null || cleApi.isBlank()) {
            System.err.println("""
                    La variable d'environnement OPENAI_API_KEY n'est pas definie.
                    Ajoutez-la dans les variables d'environnement utilisateur de Windows,
                    puis relancez IntelliJ (les variables sont lues au demarrage).""");
            return;
        }

        // Modele integre : tourne en local (ONNX), ne coute rien en tokens.
        // Le premier appel est lent, le temps de charger le modele en memoire.
        System.out.println("Chargement du modele d'embeddings local...");
        EmbeddingModel modeleLocal = new BgeSmallEnV15QuantizedEmbeddingModel();

        // Modele externe d'OpenAI : multilingue, consomme des tokens.
        EmbeddingModel modeleExterne = OpenAiEmbeddingModel.builder()
                .apiKey(cleApi)
                .modelName(NOM_MODELE_EMBEDDINGS)
                .build();

        System.out.println();
        System.out.println("Similarite cosinus pour chaque couple de phrases.");
        System.out.println("  local   = BgeSmallEnV15Quantized (integre, anglais)");
        System.out.println("  externe = " + NOM_MODELE_EMBEDDINGS + " (OpenAI, multilingue)");
        System.out.println();

        for (String[] couple : COUPLES) {
            String libelle = couple[0];
            String phrase1 = couple[1];
            String phrase2 = couple[2];

            double similariteLocale = similarite(modeleLocal, phrase1, phrase2);
            double similariteExterne = similarite(modeleExterne, phrase1, phrase2);

            System.out.println(libelle);
            System.out.println("   A : " + phrase1);
            System.out.println("   B : " + phrase2);
            System.out.printf("   local   : %.4f%n", similariteLocale);
            System.out.printf("   externe : %.4f%n", similariteExterne);
            System.out.println();
        }

        // Taille des vecteurs produits par chaque modele, a titre d'information.
        int dimensionLocale = modeleLocal.embed("test").content().vector().length;
        int dimensionExterne = modeleExterne.embed("test").content().vector().length;
        System.out.println("Dimension des vecteurs :");
        System.out.println("  local   : " + dimensionLocale);
        System.out.println("  externe : " + dimensionExterne);
    }

    /**
     * Calcule la similarite cosinus entre les embeddings de deux phrases.
     *
     * @param modele  le modele d'embeddings a utiliser.
     * @param phrase1 la premiere phrase.
     * @param phrase2 la deuxieme phrase.
     * @return la similarite cosinus des deux embeddings.
     */
    private static double similarite(EmbeddingModel modele, String phrase1, String phrase2) {
        // embed() retourne un Response<Embedding> ; content() en extrait l'embedding.
        Embedding embedding1 = modele.embed(phrase1).content();
        Embedding embedding2 = modele.embed(phrase2).content();
        return CosineSimilarity.between(embedding1, embedding2);
    }
}
