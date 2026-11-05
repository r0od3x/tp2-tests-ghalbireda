# Utilisation de l'IA pendant le TP 2

Ce fichier regroupe les questions que j'ai posees a une IA (Claude) pendant la
realisation du TP 2, avec ses reponses. Je l'ai utilisee pour comprendre les
notions nouvelles de LangChain4j, et surtout pour debloquer les erreurs que j'ai
rencontrees. J'ajoute une section par test, au fur et a mesure.

---

## Test 1 : premier contact avec LangChain4j

### Q : Dans l'exemple du cours, pourquoi on ecrit `ChatModel modele = OpenAiChatModel.builder()...` et pas `OpenAiChatModel modele = ...` ?

`ChatModel` est une interface de LangChain4j, `OpenAiChatModel` est une des
classes qui l'implementent (il y a aussi `GoogleGenAiChatModel`, `OllamaChatModel`,
etc.). En declarant la variable avec le type de l'interface, tout le reste de mon
code ne depend que de `ChatModel` : il n'utilise que la methode `chat()`, qui
existe dans l'interface.

L'interet est concret : si je veux passer de OpenAI a Gemini, je n'ai qu'une seule
ligne a changer (celle du builder). La methode `poserQuestion` et le reste du
programme ne bougent pas. C'est exactement l'argument de portabilite donne dans
l'enonce du TP : LangChain4j ajoute une couche d'abstraction au-dessus des APIs
des differents LLMs.

### Q : Pourquoi mettre la cle d'API dans une variable d'environnement et pas directement dans le code ?

Parce que le code part sur GitHub. Une cle ecrite en dur dans un fichier `.java`
se retrouve publiee, et elle reste dans l'historique Git meme si on l'enleve dans
un commit suivant. OpenAI et Google scannent GitHub pour trouver les cles
publiees : la cle serait revoquee automatiquement, ou pire, utilisee par
quelqu'un d'autre a mes frais.

D'ou le `System.getenv("OPENAI_API_KEY")`. Deux pieges sous Windows :

- la variable doit etre definie dans les variables d'environnement
  **utilisateur**, pas seulement dans le terminal courant ;
- il faut **redemarrer IntelliJ** apres l'avoir ajoutee, parce qu'un processus
  lit les variables d'environnement au moment ou il demarre. Si on ne redemarre
  pas, `getenv` renvoie `null` alors que la variable existe bien dans le systeme.

C'est pour ca que j'ai mis un test `if (cleApi == null || cleApi.isBlank())` avec
un message explicite au lieu de laisser planter avec une exception obscure.

### Q : J'ai demande l'heure au LLM et il me repond qu'il ne peut pas la donner. C'est normal ?

Oui, c'est le resultat attendu par l'enonce. Voici ce que j'ai obtenu :

```
== Question :
Quelle heure est-il ?
== Reponse :
Je suis desole, mais je ne peux pas fournir l'heure actuelle. Vous pouvez
verifier l'heure sur votre appareil ou une horloge a proximite.
```

Un LLM est une fonction qui predit du texte a partir du texte qu'on lui envoie.
Il n'a aucun acces a l'exterieur : pas d'horloge, pas de reseau, pas de systeme
de fichiers. Tout ce qu'il "sait" vient des donnees de son entrainement, qui
sont figees a une date passee. L'heure courante n'est donc nulle part dans la
requete qu'il recoit, et il ne peut pas aller la chercher.

Deux comportements possibles selon le modele : soit il refuse proprement (c'est
le cas ici, le modele a ete entraine a reconnaitre ce genre de question), soit il
invente une heure plausible, c'est une hallucination typique.

Pour qu'il puisse vraiment repondre, il faudrait lui donner un **outil** : une
methode Java annotee `@Tool` qui renvoie l'heure, que le LLM peut demander a
l'application d'appeler. C'est l'objet du test 7, et c'est la meme logique pour
la meteo : l'outil sert a combler ce que le LLM ne peut pas savoir tout seul.

### Q : Je lui dis mon prenom dans une question, et a la question suivante il ne s'en souvient plus. Pourquoi ?

Resultat obtenu :

```
== Question :
Bonjour, je m'appelle Reda.
== Reponse :
Bonjour, Reda ! Comment puis-je vous aider aujourd'hui ?

== Question :
Comment est-ce que je m'appelle ?
== Reponse :
Je ne sais pas comment tu t'appelles, mais si tu veux, tu peux me le dire !
```

Chaque appel a `modele.chat(question)` est une requete HTTP independante, et
l'API du LLM est **sans etat** (stateless) : elle ne garde rien entre deux
requetes. Le serveur recoit ma deuxieme question tout seule, sans la premiere.
De son point de vue, la conversation commence a "Comment est-ce que je
m'appelle ?", et il n'a effectivement aucun moyen de savoir.

Ca peut surprendre parce que ChatGPT ou Gemini en version site web semblent se
souvenir. En realite c'est leur interface qui renvoie tout l'historique a chaque
message. La memoire est du cote **client**, jamais du cote API.

Donc pour avoir une conversation, c'est a mon application de renvoyer les
messages precedents a chaque requete. C'est ce que je faisais a la main avec le
JSON dans le TP 1. LangChain4j automatise ca avec `ChatMemory`, et notamment
`MessageWindowChatMemory.withMaxMessages(20)` qui garde les 20 derniers messages
(fenetre glissante, pour ne pas faire grossir indefiniment le nombre de tokens
envoyes, et donc le cout). Je l'utiliserai a partir du test 5.

### Q : Les accents s'affichent en `?` dans la console : `Je suis d?sol?` au lieu de `Je suis desole`. C'est un bug dans mon code ?

Non, le code est correct et la reponse du LLM est correcte aussi, le probleme
est a l'affichage, et seulement sous Windows.

La console Windows utilise par defaut un encodage historique (cp1252 / cp850)
alors que le texte renvoye par l'API est en UTF-8. Les caracteres accentues,
codes sur deux octets en UTF-8, ne correspondent a rien dans la table de la
console, qui affiche donc `?`. Les caracteres non accentues passent sans
probleme, ce qui explique que seuls les accents soient touches.

Ca se verifie facilement : en redirigeant la sortie vers un fichier et en
ouvrant le fichier dans un editeur en UTF-8, les accents sont intacts. Donc
aucune donnee n'est perdue, c'est purement l'affichage.

Solutions, de la plus simple a la plus propre :

1. lancer depuis IntelliJ, qui utilise UTF-8 pour la console de `Run` ;
2. ajouter l'option `-Dfile.encoding=UTF-8` a la JVM ;
3. dans un terminal Windows, taper `chcp 65001` avant de lancer le programme.

J'ai garde le lancement par IntelliJ (fichier de configuration `.run/Test1.run.xml`).
A retenir pour les tests suivants : si je vois des `?` dans une reponse, c'est
l'encodage de la console, pas le LLM ni mon code.

### Q : Au demarrage j'avais un avertissement `SLF4J(W): No SLF4J providers were found`. Je dois m'inquieter ?

Non. SLF4J est une **facade** de logging : c'est une API que les bibliotheques
(ici LangChain4j) utilisent pour ecrire leurs logs, sans choisir a la place du
developpeur quelle implementation fait reellement le travail. Si aucune
implementation n'est dans le classpath, SLF4J le signale et desactive les logs
(logger "NOP", no-operation). Le programme fonctionne, mais on ne voit aucun
log.

En ajoutant la dependance `logback-classic` dans le `pom.xml`, SLF4J trouve une
implementation, l'avertissement disparait et les logs s'affichent vraiment. La
configuration se fait alors dans `src/main/resources/logback.xml` :
l'`appender` definit la destination (ici la console) et le format des messages,
et les `logger` fixent le niveau par package. Celui sur `dev.langchain4j` en
`INFO` me permettra plus tard de monter ou descendre le niveau de detail de
LangChain4j independamment du reste de l'application, ce qui sera tres utile au
test 7 pour observer les echanges JSON lies aux outils.
