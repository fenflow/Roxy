package net.rasanovum.roxy.legacy.loader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Redirects platform types in the user's binary, without copying its implementations. */
final class VoxyPlatformPatch {

    static final String BRIDGE = "net/rasanovum/roxy/legacy/modern/";
    private static final Map<String, String> TYPES = new LinkedHashMap<String, String>();

    static {
        TYPES.put("me/cortex/voxy/common/world/other/Mapper$StateEntry", BRIDGE + "WorldMapper$StateEntry");
        TYPES.put("me/cortex/voxy/common/world/other/Mapper$BiomeEntry", BRIDGE + "WorldMapper$BiomeEntry");
        TYPES.put("me/cortex/voxy/common/world/other/Mapper", BRIDGE + "WorldMapper");
        TYPES.put("me/cortex/voxy/commonImpl/VoxyCommon", BRIDGE + "PlatformFlags");
        TYPES.put("me/cortex/voxy/common/Logger", BRIDGE + "PlatformLog");
        TYPES.put("net/minecraft/class_2680", BRIDGE + "BlockState");
        TYPES.put("net/minecraft/class_2960", BRIDGE + "ResourceId");
        platform("net/minecraft/class_4587$class_4665", "Pose");
        platform("net/minecraft/class_4587", "PoseStack");
        platform("net/minecraft/class_4588", "VertexConsumer");
        platform("net/minecraft/class_11515", "Layer");
        platform("net/minecraft/class_2248", "Block");
        platform("net/minecraft/class_2404", "LiquidBlock");
        platform("net/minecraft/class_2397", "LeavesBlock");
        platform("net/minecraft/class_2510", "StairBlock");
        platform("net/minecraft/class_3610", "Fluid");
        platform("net/minecraft/class_4696", "Layers");
        platform("net/minecraft/class_2350", "Direction");
        platform("net/caffeinemc/mods/sodium/api/util/ColorMixer", "ColorMixer");
        TYPES.put("net/caffeinemc/mods/sodium/client/util/color/ColorSRGB", BRIDGE + "ColourSpace");
        TYPES.put("net/minecraft/class_9848", BRIDGE + "ColourSpace");
        platform("net/minecraft/class_2338", "Position");
        platform("net/minecraft/class_3532", "MathHelper");
        platform("net/minecraft/class_1922", "BlockGetter");
        platform("net/minecraft/class_1920", "TintGetter");
        platform("net/minecraft/class_6539", "ColorResolver");
        platform("net/minecraft/class_322", "BlockColor");
        platform("net/minecraft/class_2586", "BlockEntity");
        platform("net/minecraft/class_3568", "LightEngine");
        platform("net/minecraft/class_1944", "LightLayer");
        TYPES.put("net/minecraft/class_310", BRIDGE + "PlatformGame");
        game("315", "Options");
        game("7172", "Option");
        game("9779", "Timer");
        game("4184", "Camera");
        game("761", "LevelRenderer");
        game("757", "Renderer");
        game("638", "World");
        game("1044", "Texture");
        game("1059", "Atlas");
        game("1060", "Textures");
        game("2561", "Text");
        game("5250", "PlainText");
        game("7594", "Chat");
        game("5321", "Key");
        game("1959", "Biome");
        game("1972", "Biomes");
        game("7924", "Registries");
        game("6880$class_6883", "Holder");
        game("2378", "Registry");
        game("5455", "RegistryAccess");
        game("7922", "BlockRegistry");
        game("7923", "Builtins");
        game("324", "Colours");
        game("2361", "ColourIds");
        TYPES.put("me/cortex/voxy/client/VoxyClient", BRIDGE + "PlatformClient");
        TYPES.put("me/cortex/voxy/client/core/rendering/util/LightMapHelper", BRIDGE + "PlatformLightmap");
        TYPES.put("me/cortex/voxy/client/config/VoxyConfig", BRIDGE + "PlatformConfig");
        TYPES.put("me/cortex/voxy/client/core/RenderPipelineFactory", BRIDGE + "ShaderPipelineFactory");
        for (String nested : new String[] { "$StructLayout", "$ImageSet", "$SSBOSet", "" }) TYPES.put(
            "me/cortex/voxy/client/iris/IrisVoxyRenderPipelineData" + nested,
            BRIDGE + "ShaderPipelineData" + nested);
        TYPES.put("net/irisshaders/iris/shaderpack/materialmap/WorldRenderingSettings", BRIDGE + "ShaderMaterials");
        TYPES.put("net/irisshaders/iris/shaderpack/ShaderPack", "net/coderbot/iris/shaderpack/ShaderPack");
        TYPES.put(
            "net/irisshaders/iris/shaderpack/include/AbsolutePackPath",
            "net/coderbot/iris/shaderpack/include/AbsolutePackPath");
        TYPES.put("me/cortex/voxy/client/core/util/IrisUtil", BRIDGE + "PlatformClient$Iris");
        TYPES.put("net/fabricmc/loader/api/FabricLoader", BRIDGE + "PlatformClient$Fabric");
        TYPES.put("com/mojang/blaze3d/opengl/GlStateManager", BRIDGE + "PlatformClient$GLState");
        TYPES.put("net/caffeinemc/mods/sodium/client/util/FogParameters", BRIDGE + "PlatformClient$Fog");
    }

    private static void platform(String original, String nested) {
        TYPES.put(original, BRIDGE + "PlatformTypes$" + nested);
    }

    private static void game(String number, String nested) {
        TYPES.put("net/minecraft/class_" + number, BRIDGE + "PlatformGame$" + nested);
    }

    private VoxyPlatformPatch() {}

    static String mappedClass(String name) {
        String mapped = TYPES.get(name.replace('.', '/'));
        return mapped == null ? name : mapped.replace('/', '.');
    }

    static byte[] apply(byte[] source) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(source));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(source.length + 512);
        DataOutputStream out = new DataOutputStream(bytes);
        if (in.readInt() != 0xCAFEBABE) throw new IOException("Invalid external class header");
        out.writeInt(0xCAFEBABE);
        out.writeShort(in.readUnsignedShort());
        out.writeShort(in.readUnsignedShort());
        int count = in.readUnsignedShort();
        out.writeShort(count);
        for (int i = 1; i < count; i++) {
            int tag = in.readUnsignedByte();
            out.writeByte(tag);
            switch (tag) {
                case 1:
                    String value = in.readUTF();
                    for (Map.Entry<String, String> entry : TYPES.entrySet()) {
                        if (value.equals(entry.getKey())) value = entry.getValue();
                        else value = value.replace("L" + entry.getKey() + ";", "L" + entry.getValue() + ";")
                            .replace("L" + entry.getKey() + "<", "L" + entry.getValue() + "<");
                    }
                    out.writeUTF(value);
                    break;
                case 3:
                case 4:
                case 9:
                case 10:
                case 11:
                case 12:
                case 17:
                case 18:
                    out.writeInt(in.readInt());
                    break;
                case 5:
                case 6:
                    out.writeLong(in.readLong());
                    i++;
                    break;
                case 7:
                case 8:
                case 16:
                case 19:
                case 20:
                    out.writeShort(in.readUnsignedShort());
                    break;
                case 15:
                    out.writeByte(in.readUnsignedByte());
                    out.writeShort(in.readUnsignedShort());
                    break;
                default:
                    throw new IOException("Unsupported external constant-pool tag " + tag);
            }
        }
        byte[] tail = new byte[in.available()];
        in.readFully(tail);
        out.write(tail);
        return bytes.toByteArray();
    }
}
