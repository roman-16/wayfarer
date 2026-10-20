package io.github.roman16.wayfarer.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.roman16.wayfarer.Bundled;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;

public record Story(List<Quest> quests, Map<String, Memory> memories, Map<String, Person> people) {
    private static final Bundled<Story> STORY = Bundled.json("/wayfarer/story.json", Story::load);
    private static final Set<String> HALL_KINDS = Set.of("shard", "vision", "worm");
    private static final Set<String> STAGE = Set.of("faces", "leaves", "moves", "poses");

    public record Person(String id, String name, @Nullable String unnamed, ChatFormatting color, List<String> mornings, List<String> remarks, boolean voice,
            @Nullable String people, List<String> cold, List<String> welcomes, Map<String, String> renamed, Map<String, List<String>> acts, List<String> shouts, @Nullable String lends) {}

    public record Line(@Nullable String by, String text, List<String> names, @Nullable String sound, @Nullable String music, boolean pause,
            @Nullable String vanishes, @Nullable ResourceLocation left, @Nullable String when, @Nullable JsonObject stage) {
        public Line(@Nullable String by, String text) {
            this(by, text, List.of(), null, null, false, null, null, null, null);
        }
    }

    public record Choice(String id, String text, String reply, @Nullable String story, @Nullable String stone, @Nullable String refused,
            boolean again, List<String> takes, Map<String, Integer> trusts, @Nullable Reward reward, @Nullable String needs, boolean narrated, @Nullable String leaves,
            @Nullable String when) {
        public Choice(String id, String text, String reply, @Nullable String story, @Nullable String stone, @Nullable String refused) {
            this(id, text, reply, story, stone, refused, false, List.of(), Map.of(), null, null, false, null, null);
        }
    }

    public record Item(@Nullable ResourceLocation id, @Nullable ResourceLocation tag, int count, @Nullable String name, @Nullable String book, boolean own,
            @Nullable ResourceLocation potion, @Nullable String tooltip, @Nullable ResourceLocation instrument, @Nullable String dyed, @Nullable String mark, @Nullable String map,
            @Nullable String tent) {}

    public record Reward(String key, String title, String text, String claimed, List<Item> items) {}

    public record Clue(String id, String text, boolean last, @Nullable String opens) {}

    public record Step(
            String id,
            String kind,
            String objective,
            List<Line> lines,
            List<Line> done,
            List<Choice> choices,
            List<Clue> clues,
            List<String> story,
            Map<String, String> people,
            Map<String, String> words,
            Map<String, List<String>> remarks,
            @Nullable Reward reward,
            JsonObject spec) {

        public String text(String key) {
            return spec.get(key).getAsString();
        }

        public List<Line> lines(String key) {
            return list(spec, key, Story::line);
        }

        public int number(String key) {
            return spec.get(key).getAsInt();
        }

        @Nullable
        public String with() {
            if (spec.has("with")) {
                return spec.get("with").getAsString();
            }
            return lines.stream().map(Line::by).filter(by -> by != null).findFirst().orElse(null);
        }
    }

    public record Memory(String id, String title, String place, List<Item> kit, @Nullable String music, boolean night, List<Step> steps) {}

    public record Quest(String id, String kind, String act, List<String> after, List<String> when, int halls, List<Line> road, @Nullable String found,
            @Nullable String read, String title, Map<String, String> renamed, String summary, @Nullable Reward reward, List<Step> steps) {
        public boolean hall() {
            return steps.stream().anyMatch(step -> HALL_KINDS.contains(step.kind()));
        }
    }

    public Optional<Quest> quest(String id) {
        return quests.stream().filter(quest -> quest.id().equals(id)).findFirst();
    }

    public Memory memory(String id) {
        Memory memory = memories.get(id);
        if (memory == null) {
            throw new IllegalArgumentException("story.json names the memory " + id + ", which story/quests.toml does not hold");
        }
        return memory;
    }

    public Person person(String id) {
        Person person = people.get(id);
        if (person == null) {
            throw new IllegalArgumentException("story.json names " + id + ", who is not in story/people.toml");
        }
        return person;
    }

    public static Story get() {
        return STORY.get();
    }

    public List<String> places() {
        return quests.stream().flatMap(quest -> quest.steps().stream())
                .flatMap(step -> Stream.of("place", "at").filter(step.spec()::has).map(step::text))
                .filter(name -> !name.equals(Caravan.NAME)).distinct().toList();
    }

    private static Story load(JsonObject json) {
        Map<String, Person> people = new LinkedHashMap<>();
        json.getAsJsonObject("people").entrySet().forEach(entry -> {
            JsonObject person = entry.getValue().getAsJsonObject();
            people.put(entry.getKey(), new Person(entry.getKey(), person.get("name").getAsString(),
                    person.has("unnamed") ? person.get("unnamed").getAsString() : null, ChatFormatting.getByName(person.get("color").getAsString()),
                    strings(person, "mornings"), strings(person, "remarks"), person.has("voice") && person.get("voice").getAsBoolean(),
                    person.has("people") ? person.get("people").getAsString() : null, strings(person, "cold"), strings(person, "welcomes"), names(person),
                    acts(person), strings(person, "shouts"), person.has("lends") ? person.get("lends").getAsString() : null));
        });
        List<Quest> quests = json.getAsJsonArray("quests").asList().stream().map(JsonElement::getAsJsonObject).map(Story::quest).toList();
        Map<String, Memory> memories = new LinkedHashMap<>();
        list(json, "memories", Story::memory).forEach(memory -> memories.put(memory.id(), memory));
        return new Story(quests, Map.copyOf(memories), Map.copyOf(people));
    }

    private static Map<String, String> names(JsonObject person) {
        Map<String, String> renamed = new LinkedHashMap<>();
        if (person.has("renamed")) {
            person.getAsJsonObject("renamed").entrySet().forEach(entry -> renamed.put(entry.getKey(), entry.getValue().getAsString()));
        }
        return renamed;
    }

    private static Quest quest(JsonObject quest) {
        String id = quest.get("id").getAsString();
        List<Step> steps = quest.getAsJsonArray("steps").asList().stream().map(JsonElement::getAsJsonObject).map(step -> step(id, step)).toList();
        return new Quest(id, quest.get("kind").getAsString(), quest.get("act").getAsString(), strings(quest, "after"),
                strings(quest, "when"), quest.has("halls") ? quest.get("halls").getAsInt() : 0, list(quest, "road", Story::line),
                quest.has("found") ? quest.get("found").getAsString() : null, quest.has("read") ? quest.get("read").getAsString() : null,
                quest.get("title").getAsString(), texts(quest, "renamed"),
                quest.get("summary").getAsString(), reward(id, quest), steps);
    }

    private static Memory memory(JsonObject memory) {
        String id = memory.get("id").getAsString();
        return new Memory(id, memory.get("title").getAsString(), memory.get("place").getAsString(), list(memory, "kit", Story::item),
                memory.has("music") ? memory.get("music").getAsString() : null, flag(memory, "night"),
                memory.getAsJsonArray("steps").asList().stream().map(JsonElement::getAsJsonObject).map(step -> step("memory/" + id, step)).toList());
    }

    private static Step step(String quest, JsonObject step) {
        String id = step.get("id").getAsString();
        return new Step(id, step.get("kind").getAsString(), step.get("objective").getAsString(), list(step, "lines", Story::line),
                list(step, "done", Story::line), list(step, "choices", choice -> choice(quest + "/" + id, choice)), list(step, "clues", Story::clue),
                strings(step, "story"), texts(step, "people"), texts(step, "words"), remarks(step), reward(quest + "/" + id, step), step);
    }

    private static Line line(JsonObject line) {
        return new Line(line.has("by") ? line.get("by").getAsString() : null, line.get("text").getAsString(), strings(line, "names"),
                line.has("sound") ? line.get("sound").getAsString() : null, line.has("music") ? line.get("music").getAsString() : null, flag(line, "pause"),
                line.has("vanishes") ? line.get("vanishes").getAsString() : null, line.has("left") ? ResourceLocation.parse(line.get("left").getAsString()) : null,
                line.has("when") ? line.get("when").getAsString() : null, STAGE.stream().anyMatch(line::has) ? line : null);
    }

    private static Choice choice(String step, JsonObject choice) {
        String id = choice.get("id").getAsString();
        return new Choice(id, choice.get("text").getAsString(), choice.get("reply").getAsString(),
                choice.has("story") ? choice.get("story").getAsString() : null, choice.has("stone") ? choice.get("stone").getAsString() : null,
                choice.has("refused") ? choice.get("refused").getAsString() : null, flag(choice, "again"), strings(choice, "takes"),
                trusts(choice), reward(step + "/" + id, choice), choice.has("needs") ? choice.get("needs").getAsString() : null, flag(choice, "narrated"),
                choice.has("leaves") ? choice.get("leaves").getAsString() : null, choice.has("when") ? choice.get("when").getAsString() : null);
    }

    static Map<String, Integer> trusts(JsonObject owner) {
        Map<String, Integer> trusts = new LinkedHashMap<>();
        if (owner.has("trusts")) {
            owner.getAsJsonObject("trusts").entrySet().forEach(entry -> trusts.put(entry.getKey(), entry.getValue().getAsInt()));
        }
        return Map.copyOf(trusts);
    }

    private static boolean flag(JsonObject owner, String key) {
        return owner.has(key) && owner.get(key).getAsBoolean();
    }

    private static Clue clue(JsonObject clue) {
        return new Clue(clue.get("id").getAsString(), clue.get("text").getAsString(), flag(clue, "last"), clue.has("opens") ? clue.get("opens").getAsString() : null);
    }

    @Nullable
    private static Reward reward(String key, JsonObject owner) {
        if (!owner.has("reward")) {
            return null;
        }
        JsonObject reward = owner.getAsJsonObject("reward");
        List<Item> items = list(reward, "items", Story::item);
        return new Reward(key, reward.get("title").getAsString(), reward.get("text").getAsString(), reward.get("claimed").getAsString(), items);
    }

    private static Item item(JsonObject item) {
        return new Item(item.has("id") ? ResourceLocation.parse(item.get("id").getAsString()) : null,
                item.has("tag") ? ResourceLocation.parse(item.get("tag").getAsString()) : null, item.has("count") ? item.get("count").getAsInt() : 1,
                item.has("name") ? item.get("name").getAsString() : null, item.has("book") ? item.get("book").getAsString() : null,
                flag(item, "own"), item.has("potion") ? ResourceLocation.parse(item.get("potion").getAsString()) : null,
                item.has("tooltip") ? item.get("tooltip").getAsString() : null, item.has("instrument") ? ResourceLocation.parse(item.get("instrument").getAsString()) : null,
                item.has("dyed") ? item.get("dyed").getAsString() : null, item.has("mark") ? item.get("mark").getAsString() : null,
                item.has("map") ? item.get("map").getAsString() : null, item.has("tent") ? item.get("tent").getAsString() : null);
    }

    private static <T> List<T> list(JsonObject owner, String key, Function<JsonObject, T> read) {
        return owner.has(key) ? owner.getAsJsonArray(key).asList().stream().map(JsonElement::getAsJsonObject).map(read).toList() : List.of();
    }

    private static Map<String, List<String>> acts(JsonObject person) {
        Map<String, List<String>> acts = new LinkedHashMap<>();
        if (person.has("acts")) {
            person.getAsJsonObject("acts").keySet().forEach(act -> acts.put(act, strings(person.getAsJsonObject("acts"), act)));
        }
        return Map.copyOf(acts);
    }

    private static List<String> strings(JsonObject owner, String key) {
        if (!owner.has(key)) {
            return List.of();
        }
        JsonElement value = owner.get(key);
        JsonArray array = value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
        if (!value.isJsonArray()) {
            array.add(value);
        }
        return array.asList().stream().map(JsonElement::getAsString).toList();
    }

    private static Map<String, List<String>> remarks(JsonObject step) {
        Map<String, List<String>> remarks = new LinkedHashMap<>();
        if (step.has("remarks")) {
            step.getAsJsonObject("remarks").keySet().forEach(person -> remarks.put(person, strings(step.getAsJsonObject("remarks"), person)));
        }
        return remarks;
    }

    private static Map<String, String> texts(JsonObject owner, String key) {
        Map<String, String> texts = new LinkedHashMap<>();
        if (owner.has(key)) {
            owner.getAsJsonObject(key).entrySet().forEach(entry -> texts.put(entry.getKey(), entry.getValue().getAsString()));
        }
        return texts;
    }
}
