// util/Ids.java
package com.comp5348.store.util;

import java.util.UUID;
public final class Ids {
    private Ids() {}
    public static String newKey() { return UUID.randomUUID().toString(); }
}
