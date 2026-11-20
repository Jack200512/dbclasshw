package com.dbclasshw.document;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Small schema allowlist matching the enabled editor extensions; no HTML authority. */
@Component
public class RichTextContent {
    private static final Set<String> BLOCKS=Set.of("paragraph","heading","bulletList","orderedList");

    public String serialize(JsonNode content) {
        if (content == null || !content.isObject() || !"doc".equals(content.path("type").asText())) invalid();
        String json=content.toString();
        if (json.getBytes(StandardCharsets.UTF_8).length > 60000) invalid();
        validate(content,0,new int[]{0});
        return json;
    }

    private void validate(JsonNode node,int depth,int[] count) {
        if (depth>24 || ++count[0]>4000 || !node.isObject()) invalid();
        String type=node.path("type").asText();
        Set<String> keys="text".equals(type)?Set.of("type","text","marks"):
                "hardBreak".equals(type)?Set.of("type","marks"):
                Set.of("type","content","attrs");
        node.fieldNames().forEachRemaining(key -> { if (!keys.contains(key)) invalid(); });
        if (node.has("marks")) {
            if (!node.get("marks").isArray() || node.get("marks").size()>2) invalid();
            for (JsonNode mark:node.get("marks")) {
                if (!mark.isObject() || mark.size()!=1 || !Set.of("bold","italic").contains(mark.path("type").asText())) invalid();
            }
        }
        if ("text".equals(type)) {
            if (!node.path("text").isTextual() || node.path("text").asText().isEmpty()) invalid();
            return;
        }
        if (!Set.of("doc","paragraph","heading","bulletList","orderedList","listItem","hardBreak").contains(type)) invalid();
        JsonNode attrs=node.get("attrs");
        if (attrs!=null) {
            if (!attrs.isObject()) invalid();
            if ("heading".equals(type)) {
                if (attrs.size()!=1 || !attrs.path("level").isInt() || attrs.get("level").intValue()<1 || attrs.get("level").intValue()>3) invalid();
            } else if ("orderedList".equals(type)) {
                attrs.fieldNames().forEachRemaining(key -> {if (!Set.of("start","type").contains(key)) invalid();});
                if (attrs.has("start") && (!attrs.get("start").isInt() || attrs.get("start").intValue()<1)) invalid();
                if (attrs.has("type") && !attrs.get("type").isNull()) invalid();
            } else if (!attrs.isEmpty()) invalid();
        } else if ("heading".equals(type)) invalid();
        JsonNode children=node.get("content");
        if ("hardBreak".equals(type)) { if (children!=null) invalid(); return; }
        boolean container=Set.of("doc","bulletList","orderedList","listItem").contains(type);
        if (children==null) { if (container) invalid(); return; }
        if (!children.isArray() || (container && children.isEmpty())) invalid();
        for (int i=0;i<children.size();i++) {
            JsonNode child=children.get(i);
            String childType=child.path("type").asText();
            boolean allowed=switch(type) {
                case "doc" -> BLOCKS.contains(childType);
                case "paragraph","heading" -> Set.of("text","hardBreak").contains(childType);
                case "bulletList","orderedList" -> "listItem".equals(childType);
                case "listItem" -> i==0?"paragraph".equals(childType):BLOCKS.contains(childType);
                default -> false;
            };
            if (!allowed) invalid();
            validate(child,depth+1,count);
        }
    }

    private static void invalid() { throw new IllegalArgumentException("Invalid or oversized Tiptap document"); }
}
