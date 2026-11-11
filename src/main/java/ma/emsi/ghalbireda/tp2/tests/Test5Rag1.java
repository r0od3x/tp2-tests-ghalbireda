package ma.emsi.ghalbireda.tp2.tests;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Test 5 du TP 2 : RAG (Retrieval-Augmented Generation) avec EasyRAG.
 * <p>
 * Le programme se deroule en deux temps pour bien montrer l'apport du RAG :
 * <ol>
 *   <li>la meme question est d'abord posee au LLM seul : il ne peut pas repondre,
 *       l'information ne fait pas partie de son entrainement ;</li>
 *   <li>puis elle est posee a un assistant equipe d'un RAG sur infos.txt : il
 *       repond correctement.</li>
 * </ol>
 * Fonctionnement du RAG, en deux phases.
 * <p>
 * Phase d'ingestion, au demarrage et une seule fois :
 * <ul>
 *   <li>le document est charge (FileSystemDocumentLoader) ;</li>
 *   <li>il est decoupe en segments (ici le fichier est minuscule, donc peu de
 *       decoupage ; ce sera plus visible avec le PDF du test 6) ;</li>
 *   <li>l'embedding de chaque segment est calcule, puis le segment et son
 *       embedding sont ranges dans une base de donnees vectorielle, ici en
 *       memoire (InMemoryEmbeddingStore), donc perdue a la fin du programme.</li>
 * </ul>
 * Phase de recuperation, a chaque question :
 * <ul>
 *   <li>l'embedding de la question est calcule ;</li>
 *   <li>les segments dont l'embedding est le plus similaire (similarite cosinus
 *       du test 4) sont retrouves dans la base vectorielle ;</li>
 *   <li>ces segments sont ajoutes a la question avant l'envoi au LLM, qui s'en
 *       sert pour rediger sa reponse.</li>
 * </ul>
 * EasyRAG fait tout cela avec des valeurs par defaut, sans configuration : c'est
 * le role de EmbeddingStoreIngestor.ingest() et de EmbeddingStoreContentRetriever.
 */
public class Test5Rag1 {

    private static final String NOM_MODELE = "gpt-4o-mini";

    private static final double TEMPERATURE = 0.7;

    private static final String FICHIER_INFOS = "infos.txt";

    public static void main(String[] args) {
        String cleApi = System.getenv("OPENAI_API_KEY");
        if (cleApi == null || cleApi.isBlank()) {
            System.err.println("""
                    La variable d'environnement OPENAI_API_KEY n'est pas definie.
                    Ajoutez-la dans les variables d'environnement utilisateur de Windows,
                    puis relancez IntelliJ (les variables sont lues au demarrage).""");
            return;
        }

        Path chemin = Path.of(FICHIER_INFOS);
        if (!Files.exists(chemin)) {
            System.err.println("Fichier introuvable : " + chemin.toAbsolutePath()
                    + "\nIl doit se trouver a la racine du projet.");
            return;
        }

        ChatModel modele = OpenAiChatModel.builder()
                .apiKey(cleApi)
                .modelName(NOM_MODELE)
                .temperature(TEMPERATURE)
                .build();

        String question = "Comment s'appelle le chat de Pierre ?";

        // 1. Sans RAG : le LLM n'a aucune information sur Pierre ni son chat.
        System.out.println("##### SANS RAG (le LLM seul) #####");
        System.out.println("== Question :");
        System.out.println(question);
        System.out.println("== Reponse :");
        System.out.println(modele.chat(question));
        System.out.println();

        // 2. Avec RAG : phase d'ingestion du fichier infos.txt.
        System.out.println("##### AVEC RAG (infos.txt) #####");
        System.out.println("Ingestion de " + FICHIER_INFOS + "...");

        // Le parseur n'est pas precise : EasyRAG en choisit un automatiquement
        // en fonction du type du fichier (Apache Tika sait lire txt, pdf, docx...).
        Document document = FileSystemDocumentLoader.loadDocument(chemin);

        // Base de donnees vectorielle en memoire.
        EmbeddingStore<TextSegment> baseVectorielle = new InMemoryEmbeddingStore<>();

        // Decoupage + calcul des embeddings + enregistrement, avec la
        // configuration par defaut d'EasyRAG.
        EmbeddingStoreIngestor.ingest(document, baseVectorielle);
        System.out.println("Ingestion terminee.");
        System.out.println();

        // Creation de l'assistant. AiServices fabrique l'implementation de
        // l'interface Assistant, en y branchant le modele, une memoire de
        // conversation et le retrieveur qui va chercher les segments pertinents.
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(modele)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                .contentRetriever(EmbeddingStoreContentRetriever.from(baseVectorielle))
                .build();

        // La meme question que ci-dessus, cette fois avec le RAG.
        poserQuestion(assistant, question);

        // Question qui demande au LLM de combiner l'information du fichier avec
        // ses propres capacites linguistiques.
        poserQuestion(assistant, "Pierre appelle son chat. Qu'est-ce qu'il pourrait dire ?");

        // Le fichier contient aussi la capitale de la France. En remplacant
        // Paris par Marseille dans infos.txt, on voit que l'information du RAG
        // prend le dessus sur ce que le LLM a appris pendant son entrainement.
        poserQuestion(assistant, "Quelle est la capitale de la France ?");
    }

    /**
     * Pose une question a l'assistant et affiche la question puis la reponse.
     *
     * @param assistant le service IA equipe du RAG.
     * @param question  la question a poser.
     */
    private static void poserQuestion(Assistant assistant, String question) {
        System.out.println("== Question :");
        System.out.println(question);
        System.out.println("== Reponse :");
        System.out.println(assistant.chat(question));
        System.out.println();
    }
}
