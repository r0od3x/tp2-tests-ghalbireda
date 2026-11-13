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
import java.util.Scanner;

/**
 * Test 6 du TP 2 : RAG sur un fichier PDF, avec une vraie conversation.
 * <p>
 * Deux differences avec le test 5.
 * <p>
 * 1. Le document n'est plus un fichier texte de trois lignes mais le PDF du
 * support de cours. Le code est pourtant le meme : EasyRAG choisit tout seul le
 * parseur adapte au type de fichier, grace a Apache Tika qui sait lire les PDF.
 * Comme le fichier est gros, il est reellement decoupe en morceaux, et c'est la
 * que le decoupage prend son sens : seuls les morceaux similaires a la question
 * sont envoyes au LLM, ce qui evite de le noyer sous des informations inutiles
 * et de payer des milliers de tokens d'entree a chaque question.
 * <p>
 * 2. Au lieu de recompiler pour changer de question, le programme lit les
 * questions au clavier dans une boucle. La conversation est possible parce que
 * l'assistant a une memoire (MessageWindowChatMemory), deja presente au test 5 :
 * on peut donc poser une question qui fait reference a la precedente.
 * <p>
 * Pour quitter : une ligne vide, ou "exit".
 */
public class Test6Rag2 {

    private static final String NOM_MODELE = "gpt-4o-mini";

    private static final double TEMPERATURE = 0.7;

    private static final String FICHIER_PDF = "llm.pdf";

    public static void main(String[] args) {
        String cleApi = System.getenv("OPENAI_API_KEY");
        if (cleApi == null || cleApi.isBlank()) {
            System.err.println("""
                    La variable d'environnement OPENAI_API_KEY n'est pas definie.
                    Ajoutez-la dans les variables d'environnement utilisateur de Windows,
                    puis relancez IntelliJ (les variables sont lues au demarrage).""");
            return;
        }

        Path chemin = Path.of(FICHIER_PDF);
        if (!Files.exists(chemin)) {
            System.err.println("Fichier introuvable : " + chemin.toAbsolutePath()
                    + "\nCopiez le PDF du support de cours a la racine du projet.");
            return;
        }

        ChatModel modele = OpenAiChatModel.builder()
                .apiKey(cleApi)
                .modelName(NOM_MODELE)
                .temperature(TEMPERATURE)
                .build();

        // Phase d'ingestion. Plus longue qu'au test 5 : il faut extraire le
        // texte du PDF, le decouper, puis calculer l'embedding de chaque
        // segment. Les embeddings sont calcules en local par le modele integre
        // d'EasyRAG, donc cette phase ne coute rien en tokens.
        System.out.println("Ingestion de " + FICHIER_PDF + " (cela peut prendre un moment)...");
        long debut = System.currentTimeMillis();

        Document document = FileSystemDocumentLoader.loadDocument(chemin);
        EmbeddingStore<TextSegment> baseVectorielle = new InMemoryEmbeddingStore<>();
        EmbeddingStoreIngestor.ingest(document, baseVectorielle);

        long duree = System.currentTimeMillis() - debut;
        System.out.println("Ingestion terminee en " + duree + " ms.");
        System.out.println("Taille du texte extrait : "
                + document.text().length() + " caracteres.");
        System.out.println();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(modele)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                .contentRetriever(EmbeddingStoreContentRetriever.from(baseVectorielle))
                .build();

        // Boucle de conversation : plus besoin de recompiler entre deux questions.
        System.out.println("Posez vos questions sur le support de cours.");
        System.out.println("Ligne vide ou \"exit\" pour quitter.");
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

            // En mode non interactif (entree redirigee), la question n'est pas
            // reaffichee par le terminal : on l'affiche pour garder une trace.
            System.out.println(question);
            System.out.println("== Reponse :");
            System.out.println(assistant.chat(question));
            System.out.println();
        }

        System.out.println("Fin de la conversation.");
    }
}
