package ma.emsi.ghalbireda.tp2.tests;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

/**
 * Test 1 du TP 2 : premier contact avec LangChain4j.
 * <p>
 * Le programme cree un modele de chat avec le pattern "builder", pose quelques
 * questions simples et affiche les reponses du LLM.
 * <p>
 * Trois series de questions sont posees :
 * <ol>
 *   <li>Une question simple, juste pour verifier que l'acces a l'API fonctionne.</li>
 *   <li>L'heure qu'il est : le LLM n'a aucun acces au temps reel, il ne peut donc
 *       pas repondre (ou il invente une heure). Pour repondre il lui faudrait un
 *       outil, ce qui est l'objet du test 7.</li>
 *   <li>Le prenom est donne dans une premiere question, puis redemande dans une
 *       deuxieme. Chaque appel a chat() est une requete HTTP independante et
 *       l'API est sans etat : aucune memoire n'a ete associee au modele, donc le
 *       LLM a deja oublie le prenom. C'est ce probleme que ChatMemory resout
 *       (voir les tests suivants et l'application Web).</li>
 * </ol>
 */
public class Test1 {


    private static final String NOM_MODELE = "gpt-4o-mini";


    private static final double TEMPERATURE = 0.7;

    public static void main(String[] args) {
        // La cle secrete n'est jamais dans le code source : elle est lue dans une
        // variable d'environnement du systeme d'exploitation.
        String cleApi = System.getenv("OPENAI_API_KEY");
        if (cleApi == null || cleApi.isBlank()) {
            System.err.println("""
                    La variable d'environnement OPENAI_API_KEY n'est pas definie.
                    Ajoutez-la dans les variables d'environnement utilisateur de Windows,
                    puis relancez IntelliJ (les variables sont lues au demarrage).""");
            return;
        }

        // Creation du modele avec le design pattern "builder".
        ChatModel modele = OpenAiChatModel.builder()
                .apiKey(cleApi)
                .modelName(NOM_MODELE)
                .temperature(TEMPERATURE)
                .build();

        // 1. Pour se chauffer : une question simple.
        poserQuestion(modele, "Quelle est la capitale du Maroc ?");

        // 2. L'heure qu'il est : le LLM n'a pas acces au temps reel.
        poserQuestion(modele, "Quelle heure est-il ?");

        // 3. Deux questions successives : le LLM n'a pas de memoire entre elles.
        poserQuestion(modele, "Bonjour, je m'appelle Reda.");
        poserQuestion(modele, "Comment est-ce que je m'appelle ?");
    }

    /**
     * Envoie une question au LLM et affiche la question puis la reponse.
     *
     * @param modele    LLM.
     * @param question  la question a poser.
     */
    private static void poserQuestion(ChatModel modele, String question) {
        System.out.println("== Question :");
        System.out.println(question);
        System.out.println("== Reponse :");
        System.out.println(modele.chat(question));
        System.out.println();
    }
}
