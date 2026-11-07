# Utilisation de l'IA pendant le TP 2

Ce fichier regroupe les questions que j'ai posees a une IA (Claude) pendant la
realisation du TP 2, avec ses reponses. Je l'ai utilisee pour comprendre les
notions nouvelles de LangChain4j, et surtout pour debloquer les erreurs que j'ai
rencontrees. J'ajoute une section par test, au fur et a mesure.

---

## Test 1 : premier contact avec LangChain4j

### Q : pourquoi on ecrit `ChatModel modele = OpenAiChatModel.builder()...` et pas `OpenAiChatModel modele = ...` ? je vois pas lintéret

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

### Q : jpeux pas mettre ma clé direct dans le code ? les variables denvironnement cest relou

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

### Q : jai demandé lheure au llm et il me répond quil peut pas la donner, cest normal ou jai raté un truc ?

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

### Q : je lui dit mon prenom et a la question dapres il sen souvient plus??? pourquoi

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

### Q : dans la console les accents saffichent en `?`, jai `Je suis d?sol?` au lieu de desolé. cest un bug dans mon code ?

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

### Q : au lancement javais `SLF4J(W): No SLF4J providers were found`, cest grave ?

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

---

## Test 2 : cout en tokens

### Q : comment je récupere le nombre de tokens ? `modele.chat(question)` me renvoie juste une String

C'est le point de l'exercice. L'interface `ChatModel` a plusieurs methodes
`chat()` surchargees :

- `String chat(String)` : pratique, mais elle ne retourne que le texte. Les
  metadonnees de la reponse sont jetees.
- `ChatResponse chat(ChatMessage...)` et `ChatResponse chat(List<ChatMessage>)` :
  elles retournent un objet `ChatResponse`.

C'est la deuxieme qu'il faut utiliser. `ChatResponse` contient trois choses :

```java
ChatResponse reponse = modele.chat(UserMessage.from(question));
reponse.aiMessage().text()   // le texte de la reponse
reponse.tokenUsage()         // les nombres de tokens
reponse.finishReason()       // pourquoi le LLM s'est arrete
```

Donc au lieu de passer une String, j'emballe ma question dans un
`UserMessage.from(question)`. Ensuite `tokenUsage()` donne
`inputTokenCount()`, `outputTokenCount()` et `totalTokenCount()`.

A noter : `finishReason()` sera utile plus tard, c'est lui qui indique que le LLM
s'est arrete pour demander l'appel d'un outil (test 7).

### Q : pourquoi les tokens de sortie coutent 4 fois plus cher que ceux dentrée ?

Parce que le travail n'est pas le meme. Les tokens d'entree sont traites tous
ensemble, en parallele, en une seule passe dans le modele. Les tokens de sortie
sont generes un par un : pour produire le token n, le modele doit refaire une
passe complete en tenant compte des n-1 tokens deja produits. Une reponse de 100
tokens, c'est 100 passes.

Concretement pour gpt-4o-mini : 0,15 $ par million de tokens d'entree contre
0,60 $ par million en sortie, soit un rapport de 4. Le rapport est du meme ordre
chez les autres fournisseurs.

Consequence pratique : pour reduire la facture, il vaut mieux limiter la longueur
des reponses que celle des questions. Et quand une conversation s'allonge, c'est
quand meme l'entree qui finit par dominer, parce qu'on renvoie tout l'historique
a chaque requete. C'est ce qui justifie la fenetre de `MessageWindowChatMemory`.

### Q : jai eu 22 tokens en tout et donc 144 928 requetes pour 1 dollar. ca me parait enorme, jai pas fait une erreur de calcul ?

Le calcul est bon. Voici ma sortie :

```
== Tokens utilises :
  entree (question) : 14
  sortie (reponse)  : 8
  total             : 22

== Cout de la requete (tarifs gpt-4o-mini) :
  entree : 14 tokens x 0.15 $/M = 0.00000210 $
  sortie : 8 tokens x 0.60 $/M = 0.00000480 $
  total  : 0.00000690 $

== Pour depenser 1 dollar :
  il faudrait environ 144928 requetes identiques.
```

Verification a la main : 14 x 0,15 / 1 000 000 = 0,0000021 $ et
8 x 0,60 / 1 000 000 = 0,0000048 $, soit 0,0000069 $ au total. Et
1 / 0,0000069 = 144 927,5. Donc environ 145 000 requetes pour un dollar.

Mais il faut bien voir que ce chiffre est le cas le plus favorable possible :

- la question fait 6 mots et la reponse une seule phrase ;
- il n'y a aucun historique de conversation ;
- il n'y a pas de message systeme ;
- il n'y a pas de documents ajoutes par un RAG.

Des le test 5, le RAG va coller plusieurs morceaux de documents dans chaque
requete, et au test 7 chaque question avec outils provoque plusieurs
allers-retours avec tout l'historique a chaque fois. On passera facilement de 22
tokens a plusieurs milliers pour une seule question de l'utilisateur. C'est la
que le cout devient reel, et c'est exactement ce que l'enonce fait remarquer a la
fin du test 7.

### Q : jai eu un NullPointerException sur `tokens.inputTokenCount()`, ca vient dou ?

De l'autoboxing. `inputTokenCount()` ne retourne pas un `int` mais un `Integer`,
donc un objet qui peut valoir `null`. Quand j'ecris :

```java
int tokensEntree = tokens.inputTokenCount();
```

Java appelle automatiquement `.intValue()` sur l'`Integer` pour le convertir en
`int`. Si la valeur est `null`, cet appel plante avec un NPE, et le message
d'erreur ne dit pas clairement d'ou ca vient.

Pourquoi ce serait `null` : LangChain4j remplit `TokenUsage` avec ce que l'API
renvoie. Tous les fournisseurs ne renvoient pas ces champs, et en streaming ils
peuvent n'arriver qu'a la fin. Avec l'API d'OpenAI en mode normal ils sont
toujours presents, donc ca passe, mais le type `Integer` reste la pour les cas ou
ce n'est pas garanti. `reponse.tokenUsage()` lui-meme peut aussi etre `null` pour
la meme raison.

Pour ce test je garde le code simple parce que je sais que OpenAI renvoie les
compteurs. Dans du vrai code, je testerais `tokenUsage() != null` avant, ou
j'utiliserais `Integer` plutot que `int`.
