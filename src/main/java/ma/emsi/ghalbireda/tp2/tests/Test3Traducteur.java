package ma.emsi.ghalbireda.tp2.tests;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.util.Map;

/**
 * Test 3 du TP 2 : un traducteur, avec PromptTemplate et Prompt.
 * <p>
 * Un PromptTemplate est un texte de prompt qui contient des variables, notees
 * entre doubles accolades. La methode apply() remplace les variables par les
 * valeurs fournies et retourne un Prompt, dont text() donne le texte final a
 * envoyer au LLM.
 * <p>
 * L'interet par rapport a une concatenation de String : le texte du prompt est
 * ecrit une seule fois, au meme endroit, et il est reutilisable avec des valeurs
 * differentes. C'est ce mecanisme que les services IA (AiServices) utilisent en
 * interne avec les annotations @UserMessage et @V.
 * <p>
 * Deux templates sont utilises ici :
 * <ol>
 *   <li>un template a une seule variable : le texte a traduire, la langue cible
 *       etant fixee dans le template (anglais) ;</li>
 *   <li>un template a deux variables : la langue cible et le texte a traduire.</li>
 * </ol>
 */
public class Test3Traducteur {

    private static final String NOM_MODELE = "gpt-4o-mini";

    private static final double TEMPERATURE = 0.7;

    // Template a une variable : seul le texte a traduire change.
    private static final String TEMPLATE_UNE_VARIABLE =
            "Traduis le texte suivant en anglais : {{texte}}";

    // Template a deux variables : la langue cible devient un parametre.
    private static final String TEMPLATE_DEUX_VARIABLES =
            "Traduis le texte suivant en {{langue}} : {{texte}}";

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

        // 1. Template a une seule variable.
        System.out.println("### Template a une variable (traduction en anglais)");
        PromptTemplate templateAnglais = PromptTemplate.from(TEMPLATE_UNE_VARIABLE);

        traduire(modele, templateAnglais, Map.of(
                "texte", "Le chat de Pierre dort sur le canape."));
        traduire(modele, templateAnglais, Map.of(
                "texte", "Il fait tres beau aujourd'hui a Casablanca."));

        // 2. Template a deux variables : la langue devient un parametre.
        System.out.println("### Template a deux variables (langue au choix)");
        PromptTemplate templateMultilingue = PromptTemplate.from(TEMPLATE_DEUX_VARIABLES);

        String phrase = "Le chat de Pierre dort sur le canape.";
        traduire(modele, templateMultilingue, Map.of("langue", "anglais", "texte", phrase));
        traduire(modele, templateMultilingue, Map.of("langue", "espagnol", "texte", phrase));
        traduire(modele, templateMultilingue, Map.of("langue", "arabe", "texte", phrase));
    }

    /**
     * Applique les valeurs au template, envoie le prompt obtenu au LLM et
     * affiche le prompt puis la traduction.
     *
     * @param modele   LLM.
     * @param template le template de prompt du traducteur.
     * @param valeurs  les valeurs des variables du template.
     */
    private static void traduire(ChatModel modele, PromptTemplate template,
                                 Map<String, Object> valeurs) {
        // apply() remplace les {{variables}} par les valeurs de la Map.
        Prompt prompt = template.apply(valeurs);

        System.out.println("== Prompt envoye :");
        System.out.println(prompt.text());
        System.out.println("== Traduction :");
        System.out.println(modele.chat(prompt.text()));
        System.out.println();
    }
}
