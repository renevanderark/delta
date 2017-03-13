package nl.kb.delta.deposits;

import com.google.common.collect.Lists;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DepositResult {
    private final List<String> messages = Lists.newArrayList();

    boolean isStillSuccesful() {
        return messages.isEmpty();
    }

    public void addMessage(String message) {
        this.messages.add(message);
    }

    public void addMessages(Stream<String> messages) {
        this.messages.addAll(messages.collect(Collectors.toList()));
    }

    public List<String> getMessages() {
        return messages;
    }

    public boolean isSuccess() {
        return isStillSuccesful();
    }
}
