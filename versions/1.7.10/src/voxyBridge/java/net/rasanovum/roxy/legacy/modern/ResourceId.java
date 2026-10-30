package net.rasanovum.roxy.legacy.modern;

/** Resource identifier ABI used by the external shader include loader. */
public record ResourceId(String namespace, String path) {

    public static ResourceId method_60654(String id) {
        int split = id.indexOf(':');
        return split < 0 ? new ResourceId("minecraft", id)
            : new ResourceId(id.substring(0, split), id.substring(split + 1));
    }

    public static ResourceId method_60655(String namespace, String path) {
        return new ResourceId(namespace, path);
    }

    public String method_12836() {
        return namespace;
    }

    public String method_12832() {
        return path;
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}
