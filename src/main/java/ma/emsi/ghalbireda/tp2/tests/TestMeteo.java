package ma.emsi.ghalbireda.tp2.tests;

import java.util.Arrays;

/**
 * Test de la classe MeteoTool sans LLM, demande par l'enonce.
 * <p>
 * Avant de donner les outils au LLM, il faut verifier qu'ils fonctionnent tout
 * seuls : si l'outil est casse, le LLM donnera une mauvaise reponse sans qu'on
 * sache si le probleme vient de l'outil ou de la facon dont le LLM l'a appele.
 * <p>
 * Le seuil de pluie est volontairement modifie en fin de programme pour verifier
 * que la methode classe bien les jours en "pluie probable" ou non, sans avoir a
 * attendre qu'il pleuve vraiment.
 */
public class TestMeteo {

    // Paris, d'apres l'enonce.
    private static final double LATITUDE_PARIS = 48.85;
    private static final double LONGITUDE_PARIS = 2.35;

    public static void main(String[] args) {
        MeteoTool outil = new MeteoTool();

        System.out.println("### Previsions pour Paris (48.85, 2.35) sur 3 jours");
        System.out.println("Seuil de pluie : " + outil.getSeuilPluie() + "%");
        System.out.println(outil.isPluiePrevue(LATITUDE_PARIS, LONGITUDE_PARIS, 3));

        // Avec un seuil tres bas, tous les jours doivent passer en "pluie probable".
        outil.setSeuilPluie(1);
        System.out.println("### Memes previsions avec un seuil de " + outil.getSeuilPluie() + "%");
        System.out.println(outil.isPluiePrevue(LATITUDE_PARIS, LONGITUDE_PARIS, 3));

        // Avec un seuil tres haut, aucun jour ne doit etre considere comme pluvieux.
        outil.setSeuilPluie(99);
        System.out.println("### Memes previsions avec un seuil de " + outil.getSeuilPluie() + "%");
        System.out.println(outil.isPluiePrevue(LATITUDE_PARIS, LONGITUDE_PARIS, 3));

        // Test du deuxieme outil : geolocalisation par nom de ville.
        System.out.println("### Geolocalisation");
        System.out.println("Paris    : " + Arrays.toString(outil.coordonnees("Paris")));
        System.out.println("Rabat    : " + Arrays.toString(outil.coordonnees("Rabat,Morocco")));
        System.out.println("Zzyzx    : " + Arrays.toString(outil.coordonnees("Zzyzx")));

        // Ville qui n'existe pas : l'outil doit lever une exception.
        try {
            outil.coordonnees("Zzzzzzqqqq");
            System.out.println("Probleme : aucune exception pour une ville inexistante.");
        } catch (IllegalArgumentException e) {
            System.out.println("Ville inexistante : " + e.getMessage());
        }
    }
}
