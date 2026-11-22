package de.vw.mib.bap.mqbab2.common.api;
public class APIFactory {
    private static de.vw.mib.bap.mqbab2.common.api.androidauto.AndroidAutoService androidAutoService;
    public static APIFactoryInterface self;
    public static APIFactoryInterface getAPIFactory() { return self; }
    public static void stockCreate(de.vw.mib.bap.mqbab2.common.api.androidauto.AndroidAutoService s) { androidAutoService = s; }
    public static de.vw.mib.bap.mqbab2.common.api.androidauto.AndroidAutoService current() { return androidAutoService; }
}
