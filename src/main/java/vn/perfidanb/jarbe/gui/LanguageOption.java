package vn.perfidanb.jarbe.gui;

record LanguageOption(String name, String code) {
    @Override
    public String toString() {
        return name;
    }
}
