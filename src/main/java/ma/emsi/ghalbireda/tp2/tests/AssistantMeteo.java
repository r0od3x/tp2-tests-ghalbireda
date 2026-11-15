package ma.emsi.ghalbireda.tp2.tests;

/**
 * Service IA du test 7, utilise avec les outils de MeteoTool.
 * <p>
 * Comme l'interface Assistant des tests 5 et 6, elle n'est pas implementee a la
 * main : AiServices en fabrique une implementation a l'execution. La difference
 * se situe uniquement dans le builder, ou des outils sont declares avec
 * .tools(new MeteoTool()). L'interface, elle, ne change pas : du point de vue du
 * code appelant, poser une question reste un simple appel de methode.
 */
public interface AssistantMeteo {

    /**
     * Envoie une question au LLM, qui peut utiliser les outils si besoin.
     *
     * @param question la question de l'utilisateur.
     * @return la reponse du LLM.
     */
    String chat(String question);
}
