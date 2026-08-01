package bm.b0b0b0.soulAuction.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public final class AuctionPaperCommand implements BasicCommand {

    private final AuctionCommand auctionCommand;

    public AuctionPaperCommand(AuctionCommand auctionCommand) {
        this.auctionCommand = auctionCommand;
    }

    @Override
    public void execute(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        CommandSender sender = stack.getSender();
        auctionCommand.onCommand(sender, null, "ah", args);
    }

    @Override
    public @NotNull Collection<String> suggest(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        CommandSender sender = stack.getSender();
        List<String> suggestions = auctionCommand.complete(sender, "ah", args);
        return suggestions == null ? List.of() : suggestions;
    }

    @Override
    public @NotNull String permission() {
        return "soulauction.command.ah";
    }
}
