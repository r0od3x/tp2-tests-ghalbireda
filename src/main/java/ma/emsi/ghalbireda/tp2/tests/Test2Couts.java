package ma.emsi.ghalbireda.tp2.tests;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.TokenUsage;

/**
 * Test 2 du TP 2 : cout en tokens d'une requete.
 * <p>
 * Meme question que dans le test 1, mais on utilise la methode chat() qui
 * retourne une instance de ChatResponse au lieu de celle qui retourne
 * directement une String. ChatResponse donne acces au nombre de tokens
 * consommes, par l'intermediaire de TokenUsage :
 * <ul>
 *   <li>tokens d'entree : la question envoyee au LLM ;</li>
 *   <li>tokens de sortie : la reponse generee par le LLM.</li>
 * </ul>
 * Les deux ne sont pas facturees au meme prix : les tokens de sortie coutent
 * nettement plus cher que ceux d'entree, parce qu'ils doivent etre generes un
 * par un par le modele.
 * <p>
 * L'enonce demande le cout pour gemini-3.5-flash-lite ; comme ce projet utilise
 * l'API d'OpenAI, les tarifs ci-dessous sont ceux de gpt-4o-mini.
 */
public class Test2Couts {

    private static final String NOM_MODELE = "gpt-4o-mini";

    private static final double TEMPERATURE = 0.7;

    // Tarifs de gpt-4o-mini, en dollars par million de tokens.
    private static final double PRIX_ENTREE_PAR_MILLION = 0.15;
    private static final double PRIX_SORTIE_PAR_MILLION = 0.60;

    private static final int UN_MILLION = 1_000_000;

    public static void main(String[] args) {
        String cleApi = System.getenv("OPENAI_API_KEY");
        if (cleApi == null || cleApi.isBlank()) {
            System.err.println("""
                    La variable d'environnement OPENAI_API_KEY n'est pas definie.
                    Ajoutez-la dans les variables d'environnement utilisateur de Windows,
                    puis relancez IntelliJ (les variables sont lues au demarrage).""");
            return;
        }

        ChatModel modele = OpenAiChatModel.builder()
                .apiKey(cleApi)
                .modelName(NOM_MODELE)
                .temperature(TEMPERATURE)
                .build();

        String question = "Quelle est la capitale du Maroc ?";

        // Cette version de chat() prend des ChatMessage et retourne un
        // ChatResponse, qui contient la reponse ET les metadonnees (dont les
        // nombres de tokens). La version chat(String) du test 1 ne retourne
        // que le texte de la reponse, les tokens sont perdus.
        ChatResponse reponse = modele.chat(UserMessage.from(question));

        System.out.println("== Question :");
        System.out.println(question);
        System.out.println("== Reponse :");
        System.out.println(reponse.aiMessage().text());
        System.out.println();

        TokenUsage tokens = reponse.tokenUsage();
        int tokensEntree = tokens.inputTokenCount();
        int tokensSortie = tokens.outputTokenCount();

        System.out.println("== Tokens utilises :");
        System.out.println("  entree (question) : " + tokensEntree);
        System.out.println("  sortie (reponse)  : " + tokensSortie);
        System.out.println("  total             : " + tokens.totalTokenCount());
        System.out.println();

        // Calcul du cout de cette requete.
        double coutEntree = tokensEntree * PRIX_ENTREE_PAR_MILLION / UN_MILLION;
        double coutSortie = tokensSortie * PRIX_SORTIE_PAR_MILLION / UN_MILLION;
        double coutTotal = coutEntree + coutSortie;

        System.out.println("== Cout de la requete (tarifs " + NOM_MODELE + ") :");
        System.out.printf("  entree : %d tokens x %.2f $/M = %.8f $%n",
                tokensEntree, PRIX_ENTREE_PAR_MILLION, coutEntree);
        System.out.printf("  sortie : %d tokens x %.2f $/M = %.8f $%n",
                tokensSortie, PRIX_SORTIE_PAR_MILLION, coutSortie);
        System.out.printf("  total  : %.8f $%n", coutTotal);
        System.out.println();

        // Nombre de requetes identiques pour depenser 1 dollar.
        long nombreRequetesPourUnDollar = Math.round(1.0 / coutTotal);
        System.out.println("== Pour depenser 1 dollar :");
        System.out.println("  il faudrait environ " + nombreRequetesPourUnDollar
                + " requetes identiques.");
    }
}
