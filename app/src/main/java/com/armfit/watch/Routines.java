package com.armfit.watch;

/** Rutinas originales, en el orden indicado por el usuario. */
public final class Routines {
    private Routines() {}
    public static final String[] NAMES = {"Push", "Pull", "Pierna", "Upper"};
    public static final int[] DAYS = {1, 2, 3, 5};
    public static final String[][] EXERCISES = {
        {"Press inclinado con mancuernas", "Press plano en máquina", "Press militar con mancuernas",
         "Fly (peck deck)", "Laterales con mancuernas", "Extensión de tríceps en polea", "Press tras nuca en polea"},
        {"Jalón al pecho", "Remo horizontal agarre en V en máquina", "Remo agarre abierto en máquina",
         "Pull over", "Face pull", "Curl Bayesian", "Martillos", "Curl invertido"},
        {"Hack squat", "Prensa", "Peso muerto rumano", "Femoral sentado", "Extensión de cuádriceps",
         "Pantorrilla Smith", "Abductor"},
        {"Press inclinado", "Fly (peck deck)", "Press militar con mancuernas", "Laterales con mancuernas",
         "Jalón al pecho", "Remo horizontal agarre en V en máquina", "Tríceps en polea",
         "Press tras nuca en polea", "Curl Bayesian", "Curl invertido"}
    };
}
