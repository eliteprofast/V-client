import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingWriter;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.format.enigma.EnigmaDirReader;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

import java.nio.file.Path;

/**
 * Merges Fabric's official->intermediary mappings with Yarn's intermediary->named Enigma
 * mappings into one Tiny v2 file (official, intermediary, named). Elements Yarn leaves
 * unnamed keep their intermediary name so remapping never falls back to obfuscated names.
 *
 * Usage: MapTool <intermediary.tiny> <yarn/mappings dir> <output.tiny>
 */
public final class MapTool {
    public static void main(String[] args) throws Exception {
        Path intermediary = Path.of(args[0]);
        Path yarn = Path.of(args[1]);
        Path output = Path.of(args[2]);

        MemoryMappingTree officialTree = new MemoryMappingTree();
        MappingReader.read(intermediary, MappingFormat.TINY_FILE, officialTree);

        MemoryMappingTree tree = new MemoryMappingTree();
        officialTree.accept(new MappingSourceNsSwitch(tree, "intermediary"));
        EnigmaDirReader.read(yarn, "intermediary", "named", tree);

        int named = tree.getNamespaceId("named");
        for (MappingTree.ClassMapping cls : tree.getClasses()) {
            if (cls.getDstName(named) == null) cls.setDstName(cls.getSrcName(), named);
            for (MappingTree.MethodMapping method : cls.getMethods()) {
                if (method.getDstName(named) == null) method.setDstName(method.getSrcName(), named);
            }
            for (MappingTree.FieldMapping field : cls.getFields()) {
                if (field.getDstName(named) == null) field.setDstName(field.getSrcName(), named);
            }
        }

        try (MappingWriter writer = MappingWriter.create(output, MappingFormat.TINY_2_FILE)) {
            tree.accept(new MappingSourceNsSwitch(writer, "official", true));
        }
    }
}
