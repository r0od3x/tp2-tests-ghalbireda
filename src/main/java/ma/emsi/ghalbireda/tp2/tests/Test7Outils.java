package ma.emsi.ghalbireda.tp2.tests;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;

import java.util.Scanner;

/**
 * Test 7 du TP 2 : utilisation d'outils par le LLM.
 * <p>
 * Le programme commence par poser une question meteo au LLM seul, sans outil :
 * il ne peut pas y repondre, puisqu'il n'a aucune connaissance en temps reel.
 * Puis la meme question est posee a un assistant equipe des outils de MeteoTool.
 * <p>
 * La difference tient en une ligne du builder : {@code .tools(new MeteoTool())}.
 * L'interface AssistantMeteo, elle, ne change pas.
 * <p>
 * Ce qui se passe reellement pour une question avec un nom de ville, c'est une
 * suite d'allers-retours entre l'application et le LLM :
 * <ol>
 *   <li>l'application envoie la question, accompagnee de la description des deux
 *       outils ;</li>
 *   <li>le LLM repond qu'il veut appeler coordonnees("Paris") ;</li>
 *   <li>l'application execute la methode et renvoie le resultat au LLM ;</li>
 *   <li>le LLM repond qu'il veut maintenant appeler isPluiePrevue() avec ces
 *       coordonnees ;</li>
 *   <li>l'application execute et renvoie le resultat ;</li>
 *   <li>le LLM redige enfin sa reponse en francais pour l'utilisateur.</li>
 * </ol>
 * Chacun de ces echanges renvoie tout l'historique, d'ou un cout en tokens
 * nettement plus eleve qu'une simple question. C'est pour voir ces echanges que
 * logRequestsAndResponses(true) est active.
 * <p>
 * La memoire doit etre plus grande que d'habitude : les demandes d'appel d'outil
 * et leurs resultats sont eux-memes des messages qui s'ajoutent a l'historique.
 */
public class Test7Outils {

    private static final String NOM_MODELE = "gpt-4o-mini";

    private static final double TEMPERATURE = 0.7;

    private static final String QUESTION_SANS_OUTIL =
            "Je dois partir dans 2 jours a Paris ; j'y resterai 3 jours. "
                    + "Est-ce que je dois mettre un parapluie dans mes valises ?";

    /**
     * Message systeme : il fixe le role de l'assistant. Sans lui, un assistant
     * equipe d'outils meteo a tendance a vouloir tout ramener a la meteo, ou a
     * refuser les questions qui n'ont rien a voir.
     */
    private static final String ROLE_SYSTEME = """
            Tu es un assistant generaliste francophone. Tu disposes d'outils pour
            connaitre les previsions de precipitations et pour geolocaliser une
            ville ; utilise-les uniquement quand la question porte sur la pluie ou
            la meteo. Pour toute autre question, reponds normalement avec tes
            propres connaissances. Si une ville est introuvable, dis-le simplement.""";

    public static void main(String[] args) {
        String cleApi = System.getenv("OPENAI_API_KEY");
        if (cleApi == null || cleApi.isBlank()) {
            System.err.println("""
                    La variable d'environnement OPENAI_API_KEY n'est pas definie.
                    Ajoutez-la dans les variables d'environnement utilisateur de Windows,
                    puis relancez IntelliJ (les variables sont lues au demarrage).""");
            return;
        }

        // 1. Sans outil : le LLM n'a pas acces aux previsions meteo.
        System.out.println("##### SANS OUTIL #####");
        ChatModel modeleSimple = OpenAiChatModel.builder()
                .apiKey(cleApi)
                .modelName(NOM_MODELE)
                .temperature(TEMPERATURE)
                .build();

        System.out.println("== Question :");
        System.out.println(QUESTION_SANS_OUTIL);
        System.out.println("== Reponse :");
        System.out.println(modeleSimple.chat(QUESTION_SANS_OUTIL));
        System.out.println();

        // 2. Avec outils. logRequestsAndResponses(true) fait apparaitre dans les
        // logs le JSON envoye a l'API et celui recu en retour, ce qui permet de
        // suivre les demandes d'appel d'outil.
        System.out.println("##### AVEC OUTILS #####");
        ChatModel modeleAvecLogs = OpenAiChatModel.builder()
                .apiKey(cleApi)
                .modelName(NOM_MODELE)
                .temperature(TEMPERATURE)
                .logRequests(true)
                .logResponses(true)
                .build();

        AssistantMeteo assistant = AiServices.builder(AssistantMeteo.class)
                .chatModel(modeleAvecLogs)
                .systemMessageProvider(memoryId -> ROLE_SYSTEME)
                // Plus de messages que d'habitude : l'utilisation des outils
                // genere elle-meme des messages supplementaires.
                .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                .tools(new MeteoTool())
                .build();

        System.out.println("Posez vos questions. Ligne vide ou \"exit\" pour quitter.");
        System.out.println();

        Scanner clavier = new Scanner(System.in);
        while (true) {
            System.out.print("== Question : ");
            System.out.flush();

            if (!clavier.hasNextLine()) {
                break;
            }
            String question = clavier.nextLine().trim();
            if (question.isEmpty() || question.equalsIgnoreCase("exit")) {
                break;
            }

            System.out.println(question);
            System.out.println("== Reponse :");
            System.out.println(assistant.chat(question));
            System.out.println();
        }

        System.out.println("Fin de la conversation.");
    }
}
