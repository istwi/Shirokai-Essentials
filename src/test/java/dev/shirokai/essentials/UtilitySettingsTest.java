package dev.shirokai.essentials;
import dev.shirokai.essentials.config.Settings;
import dev.shirokai.essentials.persistence.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class UtilitySettingsTest {
 @TempDir Path dir;
 @Test void upgradesOldAliasesOnceAndKeepsUserSettings() throws Exception {
  var s=Settings.load(dir);var path=dir.resolve("commands.json");
  var json=com.google.gson.JsonParser.parseString(Files.readString(path)).getAsJsonObject();json.remove("utilityAliasesVersion");
  var cmd=json.getAsJsonObject("commands").getAsJsonObject("workbench");cmd.addProperty("enabled",false);cmd.add("aliases",JsonFiles.GSON.toJsonTree(java.util.List.of("mesa")));cmd.getAsJsonObject("aliasEnabled").addProperty("craft",false);
  Files.writeString(path,json.toString());s=Settings.load(dir);
  assertFalse(s.commands.commands.get("workbench").enabled);assertFalse(s.commands.commands.get("workbench").aliasEnabled.get("craft"));assertTrue(s.commands.commands.get("workbench").aliases.containsAll(java.util.List.of("mesa","craft","bancada")));assertTrue(Files.exists(dir.resolve("commands.json.bak")));
  s.commands.commands.get("workbench").aliases.remove("craft");JsonFiles.write(path,s.commands);
  assertFalse(Settings.load(dir).commands.commands.get("workbench").aliases.contains("craft"));
 }
 @Test void oldUtilityConfigGetsVisibleFiveMinuteCooldownWithoutErasingCustomFields() throws Exception {
  Settings.load(dir);Path path=dir.resolve("utilities.json");Files.writeString(path,"{\"schemaVersion\":1,\"custom\":\"keep\"}");
  assertEquals(300,Settings.load(dir).utilities.smeltCooldownSeconds);
  var j=com.google.gson.JsonParser.parseString(Files.readString(path)).getAsJsonObject();assertEquals("keep",j.get("custom").getAsString());assertTrue(j.has("smeltItems"));
 }
 @Test void newCommandsAreAddedWithoutResettingDisabledCommands() throws Exception {
  var s=Settings.load(dir);var path=dir.resolve("commands.json");var json=com.google.gson.JsonParser.parseString(Files.readString(path)).getAsJsonObject();
  var commands=json.getAsJsonObject("commands");commands.remove("cortador");commands.remove("tear");commands.remove("compactar");commands.getAsJsonObject("lixeira").addProperty("enabled",false);Files.writeString(path,json.toString());
  var loaded=Settings.load(dir);assertFalse(loaded.commands.commands.get("lixeira").enabled);var saved=com.google.gson.JsonParser.parseString(Files.readString(path)).getAsJsonObject().getAsJsonObject("commands");
  assertTrue(saved.has("cortador")&&saved.has("tear")&&saved.has("compactar"));assertFalse(saved.getAsJsonObject("lixeira").get("enabled").getAsBoolean());
 }
 @Test void invalidCooldownPreservesConfig() throws Exception {
  var s=Settings.load(dir);s.utilities.smeltCooldownSeconds=-1;var path=dir.resolve("utilities.json");JsonFiles.write(path,s.utilities);String before=Files.readString(path);assertThrows(java.io.IOException.class,()->Settings.load(dir));assertEquals(before,Files.readString(path));
 }
 @Test void smeltCooldownSurvivesStoreRestart() throws Exception {
  var id=java.util.UUID.randomUUID();var store=new DataStore(dir);store.update(id,d->d.nextSmeltAt=123456789L);assertEquals(123456789L,new DataStore(dir).player(id).nextSmeltAt);
 }
}
