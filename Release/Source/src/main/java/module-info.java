module com.vclient {
    requires java.desktop;
    requires java.logging;
    requires java.net.http;
    requires com.sun.jna;
    requires com.sun.jna.platform;
    requires javafx.controls;
    requires com.fasterxml.jackson.databind;

    exports com.vclient;
}
