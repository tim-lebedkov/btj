package clapp;

import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

public class Main {
    public static void main(String[] params) {
        I18n i18n = I18nFactory.getI18n(Main.class);
        System.out.println(i18n.tr("Hello, world!"));
        System.out.println(i18n.tr("Hello, world (untranslated)!"));
    }
}
