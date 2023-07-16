module security {
    requires java.desktop;
    requires com.google.common;
    requires java.prefs;
    requires com.google.gson;
    requires image;
    requires miglayout;
    opens com.udacity.catpoint.security.data to com.google.gson;
}
