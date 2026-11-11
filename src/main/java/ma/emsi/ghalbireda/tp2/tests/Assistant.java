package ma.emsi.ghalbireda.tp2.tests;

/**
 * Service IA : interface qui decrit les echanges entre l'application et le LLM.
 * <p>
 * Cette interface n'est jamais implementee a la main. La classe AiServices de
 * LangChain4j en fabrique une implementation a l'execution, sous la forme d'un
 * objet proxy. Chaque appel a chat() devient un echange de messages avec le LLM,
 * en tenant compte de ce qui a ete configure au moment du build : le modele, la
 * memoire de conversation, le retrieveur de contenu pour le RAG, les outils...
 * <p>
 * Le nom de la methode est libre.
 */
public interface Assistant {

    /**
     * Envoie une question au LLM et retourne sa reponse.
     *
     * @param question la question de l'utilisateur.
     * @return la reponse du LLM.
     */
    String chat(String question);
}
