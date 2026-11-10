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

---

## Test 3 : un traducteur

### Q : jai mis `{texte}` dans mon template et il remplace pas la variable, il envoie `{texte}` tel quel au llm

Il faut des doubles accolades : `{{texte}}`. Avec une seule paire, LangChain4j ne
reconnait pas la variable et laisse le texte tel quel, donc le LLM recoit
litteralement "Traduis le texte suivant en anglais : {texte}". Et comme c'est un
LLM, il ne plante pas : il repond quelque chose, souvent en traduisant le mot
"texte" ou en demandant quel texte traduire. C'est ce qui rend l'erreur penible a
reperer, il n'y a aucune exception.

La syntaxe correcte :

```java
PromptTemplate template =
        PromptTemplate.from("Traduis le texte suivant en anglais : {{texte}}");
```

Et le nom entre les accolades doit etre exactement la cle utilisee dans la Map
passee a `apply()`. Si les deux ne correspondent pas, LangChain4j leve bien une
exception, ce qui est plus facile a diagnostiquer que le cas des accolades
simples.

### Q : `template.apply()` il attend quoi comme parametre ? jai vu les 2 versions dans la javadoc

Il y a deux versions :

- `apply(Map<String, Object>)` : la Map associe chaque nom de variable a sa
  valeur. C'est la version a utiliser des qu'il y a plusieurs variables, et c'est
  celle que j'utilise partout pour rester coherent.
- `apply(Object)` : raccourci quand le template n'a qu'une seule variable.
  LangChain4j met l'objet dans la seule variable du template.

Donc pour mon template a deux variables :

```java
Prompt prompt = template.apply(Map.of("langue", "espagnol", "texte", phrase));
String reponse = modele.chat(prompt.text());
```

Attention au dernier point : `apply()` retourne un `Prompt`, pas une `String`.
Il faut appeler `prompt.text()` pour recuperer le texte a envoyer. Si on passe le
`Prompt` directement a une methode qui attend une String, ca ne compile pas.

### Q : avec le meme template jai eu "couch" la premiere fois et "sofa" la deuxieme, pour la meme phrase. normal ?

Oui, et c'est la temperature. Elle est a 0,7 comme demande par l'enonce.

Un LLM ne choisit pas le mot suivant de maniere unique : il calcule une
distribution de probabilites sur tous les tokens possibles, puis en tire un au
hasard selon cette distribution. La temperature regle a quel point ce tirage est
aleatoire :

- temperature 0 : il prend quasiment toujours le token le plus probable, les
  reponses sont presque deterministes et donc reproductibles ;
- temperature elevee : les tokens moins probables ont plus de chances de sortir,
  les reponses sont plus variees mais aussi plus risquees.

"Couch" et "sofa" sont deux traductions correctes de "canape" avec des
probabilites proches, donc a 0,7 les deux sortent. Ca n'a rien de genant ici.

En revanche, pour un vrai traducteur en production, je mettrais la temperature
plus bas (0 ou 0,2) : on veut une traduction stable, pas de la creativite. La
temperature elevee a un sens pour de la redaction ou du brainstorming.

### Q : pour larabe il ma pas juste traduit, il ma répondu `Le texte "..." se traduit en arabe par : "..."`. comment je lui fait sortir que la traduction ?

Sortie obtenue :

```
== Prompt envoye :
Traduis le texte suivant en arabe : Le chat de Pierre dort sur le canape.
== Traduction :
Le texte "Le chat de Pierre dort sur le canape." se traduit en arabe par : "..."
```

Le LLM a fait ce qu'on lui a demande, mais il a ajoute une phrase d'introduction
autour. Rien dans mon prompt ne dit de ne renvoyer que la traduction, donc il
adopte un ton d'assistant qui explique. C'est un probleme classique quand la
reponse doit etre reutilisee par un programme et pas lue par un humain.

Deux facons de corriger :

1. preciser dans le template : "Traduis le texte suivant en {{langue}}. Reponds
   uniquement avec la traduction, sans aucun commentaire ni guillemets :
   {{texte}}" ;
2. mieux : passer cette consigne dans un message systeme, qui sert justement a
   fixer le role et le format de reponse de l'assistant, et laisser le message
   utilisateur ne contenir que la donnee a traiter.

C'est la deuxieme solution que l'application Web du TP utilisera, avec le role
systeme choisi dans la liste deroulante ("Traducteur" par exemple). J'ai garde le
template simple de l'enonce pour ce test, mais ca montre bien pourquoi le message
systeme existe.

---

## Test 4 : embeddings et similarite

### Q : lenoncé dit de mettre la dependance bge-small en commentaire avant le test 5 a cause dun conflit. mais si je la commente, Test4Embeddings compile plus ? je fais comment

Le conflit est reel : `langchain4j-embeddings-bge-small-en-v15` et
`langchain4j-easy-rag` amenent tous les deux un modele d'embeddings bge-small, et
avoir les deux dans le classpath pose probleme. D'ou la consigne de l'enonce.

Mais commenter la dependance en gardant le code du test 4 qui l'utilise casse la
compilation de tout le projet, pas seulement du test 4. Et comme je dois faire un
commit par test, ca voudrait dire que les commits des tests 5 a 7 contiennent un
projet qui ne compile pas.

La solution vient de ce que `easy-rag` embarque deja le meme modele, mais en
version **quantifiee** : `langchain4j-embeddings-bge-small-en-v15-q`, avec la
classe `BgeSmallEnV15QuantizedEmbeddingModel`. Verification dans le classpath :

```
langchain4j-embeddings-bge-small-en-v15-q-1.23.0-beta33.jar
onnxruntime-1.23.2.jar
```

Donc en ecrivant le test 4 avec la version quantifiee, je n'ai besoin que de
`easy-rag` : plus de conflit, et tous les tests compilent en meme temps a chaque
commit. La dependance bge-small reste en commentaire dans le `pom.xml`, comme le
demande l'enonce, avec un commentaire qui explique pourquoi elle n'a pas besoin
d'etre reactivee.

Quantifie veut dire que les poids du modele sont stockes sur moins de bits (8 au
lieu de 32). Le fichier est plus petit et le calcul plus rapide, au prix d'une
precision legerement moindre. Pour comparer des scores de similarite entre eux,
ca ne change rien.

### Q : `modele.embed(phrase)` me rend un `Response<Embedding>` et `CosineSimilarity.between()` veut des `Embedding`, jai une erreur de compil

Il faut appeler `.content()` sur la `Response` :

```java
Embedding embedding1 = modele.embed(phrase1).content();
Embedding embedding2 = modele.embed(phrase2).content();
double similarite = CosineSimilarity.between(embedding1, embedding2);
```

`Response<T>` est un emballage que LangChain4j utilise pour renvoyer un resultat
accompagne de ses metadonnees : `content()` donne le resultat lui-meme
(l'embedding), et il y a aussi `tokenUsage()` et `finishReason()` a cote. C'est
le meme principe que `ChatResponse` au test 2, ou il fallait `aiMessage().text()`
pour arriver au texte. L'enonce le precise d'ailleurs : "pour recuperer les
embeddings a partir des reponses du modele d'embeddings, utilisez la methode
content()".

### Q : le modele local me donne 0.6771 pour 2 phrases opposées et 0.6437 pour 2 phrases qui veulent dire pareil. cest pas censé etre le contraire ??

Si, et c'est precisement le defaut que l'enonce veut faire constater. Mes
resultats :

```
1. Deux phrases similaires (meme sens, mots differents)
   A : Le chat dort sur le canape.
   B : Un felin se repose sur le sofa.
   local   : 0.6437
   externe : 0.6576

2. Deux phrases opposees (meme sujet, avis contraire)
   A : J'adore ce film, c'est un chef d'oeuvre.
   B : Je deteste ce film, c'est une catastrophe.
   local   : 0.6771
   externe : 0.6544
```

Le modele local classe donc les phrases opposees **au-dessus** des phrases
synonymes. L'ordre est inverse, et c'est l'ordre qui compte pour le RAG.

Deux raisons se cumulent :

1. `BgeSmallEnV15` a ete entraine sur de l'anglais. Sur du francais il ne
   comprend pas vraiment le sens, il reagit surtout a la forme des mots. "Le chat
   dort sur le canape" et "Un felin se repose sur le sofa" n'ont presque aucun
   mot en commun, donc il ne voit pas qu'elles disent la meme chose.
2. Meme pour un bon modele, deux phrases opposees sont intrinsequement proches :
   meme sujet, meme vocabulaire, meme structure. Le modele externe donne lui
   aussi 0,6544, ce qui reste eleve. Un embedding capture le theme bien plus que
   la polarite, et c'est une limite generale, pas un bug : pour de l'analyse de
   sentiment on n'utilise pas des embeddings bruts.

### Q : et pourquoi le local me sort 0.6286 pour 2 phrases qui ont rien a voir, alors que celui dopenai sort 0.1099 ?

```
3. Deux phrases sans aucun rapport
   A : La capitale de la France est Paris.
   B : Il faut trois oeufs pour cette recette de gateau.
   local   : 0.6286
   externe : 0.1099
```

C'est le resultat le plus parlant de ce test. Le modele externe fait son travail :
0,11, c'est-a-dire "aucun rapport". Le modele local repond 0,63 pour les memes
deux phrases, soit quasiment le meme score qu'il donnait a deux phrases
synonymes au couple 1.

Autrement dit, le modele local ecrase tout dans une bande etroite autour de 0,6
et ne distingue plus rien. Ce n'est pas que ses valeurs soient "trop hautes", le
probleme est qu'elles ne **separent** plus les cas. Pour du francais, il est
inutilisable.

La consequence pour les tests 5 et 6 est directe : EasyRAG utilise ce modele
local par defaut. Sur `infos.txt`, qui contient trois phrases, ca marche quand
meme parce qu'il suffit de ramener les trois. Sur le PDF du test 6, decoupe en
dizaines de morceaux, le RAG va souvent ramener les mauvais morceaux, et les
reponses s'en ressentiront. L'enonce previent d'ailleurs que les reponses du test
6 pourront etre decevantes, et c'est la premiere des raisons qu'il donne.

Le couple 7 confirme le diagnostic :

```
7. Une phrase et sa traduction en anglais
   A : Le chat de Pierre dort sur le canape.
   B : Pierre's cat is sleeping on the couch.
   local   : 0.4828
   externe : 0.7413
```

Une phrase et sa traduction ont exactement le meme sens, donc le score devrait
etre tres eleve. Le modele externe donne 0,74. Le modele local donne 0,48, son
score le plus bas de tout le test : il ne voit aucun lien entre une phrase et sa
traduction, alors que c'est le cas le plus evident de similarite semantique.

### Q : pourquoi le vecteur local fait 384 et celui dopenai 1536 ? ca change quoi

C'est le nombre de dimensions de l'espace dans lequel le modele place les textes.
Chaque embedding est un tableau de `float` de cette taille, et c'est une
caracteristique fixe du modele.

Plus de dimensions, c'est plus de place pour encoder des nuances, donc en general
une meilleure qualite, mais aussi plus de memoire et des calculs plus lourds
(1536 multiplications par comparaison au lieu de 384).

Le point important : **on ne peut pas comparer des embeddings qui viennent de
modeles differents.** Les dimensions ne representent pas les memes choses, et ici
les tailles ne correspondent meme pas. Consequence concrete pour le RAG : si on
change de modele d'embeddings, il faut refaire l'ingestion de tous les documents,
sinon la base vectorielle contient des vecteurs incomparables avec ceux des
questions.

### Q : jai eu `WARNING: A restricted method in java.lang.System has been called` ... `by ai.onnxruntime.OnnxRuntime`. jai un probleme ?

Non, et ca n'a rien a voir avec mon code.

Le modele local ne tourne pas en Java : c'est un vrai reseau de neurones execute
par ONNX Runtime, une bibliotheque native (du code C++). Pour la charger, le
binding Java doit appeler `System.load()`, qui ouvre une bibliotheque native
depuis la JVM.

Depuis les JDK recents, l'acces au code natif est en cours de restriction (JEP
"Integrity by Default") : la JVM previent maintenant quand une bibliotheque fait
ca sans y avoir ete autorisee explicitement, et dans une version future ce sera
bloque par defaut. Pour faire taire l'avertissement, il faudrait lancer avec
`--enable-native-access=ALL-UNNAMED`, comme le message le suggere lui-meme.

Au passage ca explique pourquoi le premier appel au modele local est nettement
plus lent que les suivants : il faut charger la bibliotheque native et les poids
du modele en memoire. Les appels suivants sont rapides, et surtout ils ne coutent
rien en tokens puisque tout se passe sur ma machine, ce qui est bien l'interet
d'un modele integre pour ce test.
