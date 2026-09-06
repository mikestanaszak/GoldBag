package io.github.mikestanaszak.goldbag.plugin;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InventoryAdapterTest {
    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }

    @BeforeAll
    static void installBukkitItemFactory() {
        ItemFactory itemFactory = (ItemFactory) Proxy.newProxyInstance(
                ItemFactory.class.getClassLoader(), new Class<?>[]{ItemFactory.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getItemMeta")) return TestMeta.proxy(new TestMeta.State());
                    if (method.getName().equals("asMetaFor")) return args[0];
                    if (method.getName().equals("isApplicable")) return true;
                    if (method.getName().equals("updateMaterial")) return args[1];
                    if (method.getName().equals("equals")) return java.util.Objects.equals(args[0], args[1]);
                    return defaultValue(method.getReturnType());
                });
        Server server = (Server) Proxy.newProxyInstance(Server.class.getClassLoader(), new Class<?>[]{Server.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getItemFactory")) return itemFactory;
                    if (method.getName().equals("getLogger")) return java.util.logging.Logger.getLogger("InventoryAdapterTest");
                    return defaultValue(method.getReturnType());
                });
        Bukkit.setServer(server);
    }

    @Test
    void pristineBaselineRejectsNonDefaultComponents() {
        InventoryAdapter adapter = new InventoryAdapter();
        ItemStack custom = new ItemStack(Material.RAW_IRON, 3);
        ItemMeta meta = custom.getItemMeta();
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(new NamespacedKey("goldbag", "custom"), PersistentDataType.STRING, "x");
        custom.setItemMeta(meta);

        assertFalse(adapter.isPlain(custom, Material.RAW_IRON));
        assertFalse(adapter.isPlain(new ItemStack(Material.RAW_IRON, 3), Material.DIAMOND));
        assertTrue(adapter.isPlain(new ItemStack(Material.RAW_IRON, 3), Material.RAW_IRON));
    }

    @Test
    void removalPlanOnlyConsumesPlainStacksAndKeepsCustomStacks() {
        InventoryAdapter adapter = new InventoryAdapter();
        InventoryFixture fixture = new InventoryFixture();
        fixture.set(0, new ItemStack(Material.RAW_IRON, 2));
        ItemStack custom = new ItemStack(Material.RAW_IRON, 4);
        ItemMeta customMeta = custom.getItemMeta();
        customMeta.setDisplayName("custom");
        custom.setItemMeta(customMeta);
        fixture.set(1, custom);

        InventoryAdapter.Plan plan = adapter.planRemoval(fixture.inventory(), Material.RAW_IRON, 2);

        assertEquals(1, plan.affectedSlots().size());
        assertTrue(plan.evidence().contains("slot=0"));
        assertTrue(plan.ready(fixture.inventory()));
        plan.apply(fixture.inventory());
        assertNull(fixture.get(0));
        assertEquals(4, fixture.get(1).getAmount());
        assertEquals("custom", fixture.get(1).getItemMeta().getDisplayName());
    }

    @Test
    void additionPlanUsesFragmentedCapacityAndRejectsFullInventory() {
        InventoryAdapter adapter = new InventoryAdapter();
        InventoryFixture fixture = new InventoryFixture();
        fixture.set(0, new ItemStack(Material.PAPER, 63));
        fixture.set(1, new ItemStack(Material.PAPER, 63));
        for (int slot = 2; slot < 36; slot++) fixture.set(slot, new ItemStack(Material.STONE, 64));

        InventoryAdapter.Plan plan = adapter.planAddition(fixture.inventory(), new ItemStack(Material.PAPER, 2));
        assertEquals(2, plan.affectedSlots().size());
        plan.apply(fixture.inventory());
        assertEquals(64, fixture.get(0).getAmount());
        assertEquals(64, fixture.get(1).getAmount());

        assertThrows(IllegalStateException.class,
                () -> adapter.planAddition(fixture.inventory(), new ItemStack(Material.PAPER, 2)));
    }

    @Test
    void planEvidenceIncludesExactMetadataAndNoteIdentity() {
        InventoryAdapter adapter = new InventoryAdapter();
        InventoryFixture fixture = new InventoryFixture();
        UUID noteId = UUID.randomUUID();
        ItemStack note = new ItemStack(Material.PAPER);
        ItemMeta meta = note.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey("goldbag", "note-id"), PersistentDataType.STRING, noteId.toString());
        note.setItemMeta(meta);
        fixture.set(4, note);
        fixture.held(4);

        InventoryAdapter.Plan plan = adapter.planHeldRemoval(fixture.inventory());
        assertEquals(4, plan.affectedSlots().get(0));
        assertTrue(plan.evidence().contains("PAPER"));
        assertTrue(plan.evidence().contains(noteId.toString()), plan.evidence());
        assertTrue(plan.evidence().contains("before="));
        assertTrue(plan.evidence().contains("after="));
    }

    @Test
    void changedStateIsRejectedWithoutMutatingAnySlot() {
        InventoryAdapter adapter = new InventoryAdapter();
        InventoryFixture fixture = new InventoryFixture();
        fixture.set(0, new ItemStack(Material.RAW_GOLD, 2));
        InventoryAdapter.Plan plan = adapter.planRemoval(fixture.inventory(), Material.RAW_GOLD, 1);
        fixture.set(20, new ItemStack(Material.DIRT, 1));

        assertFalse(plan.ready(fixture.inventory()));
        assertThrows(IllegalStateException.class, () -> plan.apply(fixture.inventory()));
        assertEquals(2, fixture.get(0).getAmount());
        assertEquals(Material.DIRT, fixture.get(20).getType());
    }

    @Test
    void wrappersUseExactPlansRatherThanImplicitInventoryOrdering() {
        InventoryAdapter adapter = new InventoryAdapter();
        InventoryFixture fixture = new InventoryFixture();
        fixture.set(0, new ItemStack(Material.PAPER, 63));
        assertTrue(adapter.canFit(fixture.inventory(), new ItemStack(Material.PAPER, 1)));
        adapter.add(fixture.inventory(), new ItemStack(Material.PAPER, 1));
        assertEquals(64, fixture.get(0).getAmount());
        adapter.remove(fixture.inventory(), Material.PAPER, 64);
        assertNull(fixture.get(0));
    }

    @Test
    void slotRemovalSupportsOffHandAndKeepsOrdinaryScansOnMainInventory() {
        InventoryAdapter adapter = new InventoryAdapter();
        InventoryFixture fixture = new InventoryFixture();
        UUID noteId = UUID.randomUUID();
        ItemStack note = new ItemStack(Material.PAPER);
        ItemMeta meta = note.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey("goldbag", "note-id"),
                PersistentDataType.STRING, noteId.toString());
        note.setItemMeta(meta);
        fixture.set(40, note);

        assertEquals(0, adapter.count(fixture.inventory(), Material.PAPER));
        assertThrows(IllegalStateException.class,
                () -> adapter.planRemoval(fixture.inventory(), Material.PAPER, 1));

        InventoryAdapter.Plan plan = adapter.planSlotRemoval(fixture.inventory(), 40);
        assertEquals(Set.of(40), new HashSet<>(plan.affectedSlots()));
        assertTrue(plan.evidence().contains("slot=40"), plan.evidence());
        assertTrue(plan.evidence().contains(noteId.toString()), plan.evidence());
        assertTrue(plan.ready(fixture.inventory()));
        plan.apply(fixture.inventory());
        assertNull(fixture.get(40));
    }

    @Test
    void slotRemovalRejectsArmorAndTracksPdcIdentityChanges() {
        InventoryAdapter adapter = new InventoryAdapter();
        InventoryFixture fixture = new InventoryFixture();
        UUID originalId = UUID.randomUUID();
        ItemStack original = note(originalId);
        fixture.set(40, original);
        InventoryAdapter.Plan plan = adapter.planSlotRemoval(fixture.inventory(), 40);

        assertThrows(IllegalArgumentException.class,
                () -> adapter.planSlotRemoval(fixture.inventory(), 9));
        fixture.set(40, note(UUID.randomUUID()));
        assertFalse(plan.ready(fixture.inventory()));
        assertEquals(originalId.toString(),
                original.getItemMeta().getPersistentDataContainer()
                        .get(new NamespacedKey("goldbag", "note-id"), PersistentDataType.STRING));
    }

    @Test
    void selectedMainHandChangeInvalidatesSlotPlan() {
        InventoryAdapter adapter = new InventoryAdapter();
        InventoryFixture fixture = new InventoryFixture();
        fixture.set(2, new ItemStack(Material.PAPER, 2));
        fixture.held(2);

        InventoryAdapter.Plan plan = adapter.planSlotRemoval(fixture.inventory(), 2);
        fixture.held(3);

        assertFalse(plan.ready(fixture.inventory()));
        assertThrows(IllegalStateException.class, () -> plan.apply(fixture.inventory()));
        assertEquals(2, fixture.get(2).getAmount());
    }

    private static ItemStack note(UUID id) {
        ItemStack note = new ItemStack(Material.PAPER);
        ItemMeta meta = note.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey("goldbag", "note-id"),
                PersistentDataType.STRING, id.toString());
        note.setItemMeta(meta);
        return note;
    }

    private static final class InventoryFixture {
        private final ItemStack[] slots = new ItemStack[41];
        private int heldSlot;
        private final PlayerInventory inventory = (PlayerInventory) Proxy.newProxyInstance(
                PlayerInventory.class.getClassLoader(), new Class<?>[]{PlayerInventory.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getItem": return slots[(Integer) args[0]] == null ? null : slots[(Integer) args[0]].clone();
                        case "setItem": slots[(Integer) args[0]] = args[1] == null ? null : ((ItemStack) args[1]).clone(); return null;
                        case "getHeldItemSlot": return heldSlot;
                        case "hashCode": return System.identityHashCode(proxy);
                        case "equals": return proxy == args[0];
                        case "toString": return "InventoryFixture";
                        default: return defaultValue(method.getReturnType());
                    }
                });

        PlayerInventory inventory() { return inventory; }
        ItemStack get(int slot) { return slots[slot]; }
        void set(int slot, ItemStack item) { slots[slot] = item == null ? null : item.clone(); }
        void held(int slot) { heldSlot = slot; }

        private static Object defaultValue(Class<?> type) {
            if (!type.isPrimitive()) return null;
            if (type == boolean.class) return false;
            if (type == byte.class) return (byte) 0;
            if (type == short.class) return (short) 0;
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            if (type == float.class) return 0F;
            if (type == double.class) return 0D;
            if (type == char.class) return '\0';
            return null;
        }
    }

    private static final class TestMeta {
        private static final class State {
            private final Set<Object> flags = new HashSet<>();
            private final Map<Object, Object> pdc = new HashMap<>();
            private String displayName;
            private boolean unbreakable;
            private State copy() {
                State result = new State();
                result.flags.addAll(flags);
                result.pdc.putAll(pdc);
                result.displayName = displayName;
                result.unbreakable = unbreakable;
                return result;
            }
        }

        private static ItemMeta proxy(State state) {
            PersistentDataContainer pdc = (PersistentDataContainer) Proxy.newProxyInstance(
                    PersistentDataContainer.class.getClassLoader(), new Class<?>[]{PersistentDataContainer.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "set": state.pdc.put(args[0], args[2]); return null;
                            case "remove": state.pdc.remove(args[0]); return null;
                            case "has": return state.pdc.containsKey(args[0]);
                            case "get": return state.pdc.get(args[0]);
                            case "isEmpty": return state.pdc.isEmpty();
                            case "getKeys": return Set.copyOf(state.pdc.keySet());
                            case "toString": return state.pdc.toString();
                            case "hashCode": return state.pdc.hashCode();
                            case "equals": return proxy == args[0];
                            default: return defaultValue(method.getReturnType());
                        }
                    });
            return (ItemMeta) Proxy.newProxyInstance(ItemMeta.class.getClassLoader(), new Class<?>[]{ItemMeta.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "clone": return proxy(state.copy());
                            case "getPersistentDataContainer": return pdc;
                            case "addItemFlags": if (args != null && args.length > 0) for (Object flag : (Object[]) args[0]) state.flags.add(flag); return null;
                            case "getItemFlags": return Set.copyOf(state.flags);
                            case "setDisplayName": state.displayName = (String) args[0]; return null;
                            case "getDisplayName": return state.displayName;
                            case "hasDisplayName": return state.displayName != null;
                            case "setUnbreakable": state.unbreakable = (Boolean) args[0]; return null;
                            case "isUnbreakable": return state.unbreakable;
                            case "hasLore": case "hasEnchants": case "hasCustomModelData": case "hasLocalizedName": case "hasAttributeModifiers": return false;
                            case "serialize": return Map.of("display", state.displayName == null ? "" : state.displayName, "flags", state.flags.toString(), "pdc", state.pdc.toString(), "unbreakable", state.unbreakable);
                            case "hashCode": return stateHash(state);
                            case "equals": return args[0] instanceof Proxy && stateHash(state) == args[0].hashCode();
                            case "toString": return "TestMeta" + stateHash(state);
                            default: return defaultValue(method.getReturnType());
                        }
                    });
        }

        private static int stateHash(State state) { return java.util.Objects.hash(state.displayName, state.unbreakable, state.flags, state.pdc); }
    }
}
