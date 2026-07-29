package bm.b0b0b0.soulAuction.api.event;

import bm.b0b0b0.soulAuction.model.AuctionListing;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class AuctionListingSoldEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final AuctionListing listing;
    private final Player buyer;
    private final int sellerPayout;
    private final int saleTax;
    private final int buyTax;

    public AuctionListingSoldEvent(AuctionListing listing, Player buyer, int sellerPayout, int saleTax, int buyTax) {
        this.listing = listing;
        this.buyer = buyer;
        this.sellerPayout = sellerPayout;
        this.saleTax = saleTax;
        this.buyTax = buyTax;
    }

    public AuctionListing listing() {
        return listing;
    }

    public Player buyer() {
        return buyer;
    }

    public int sellerPayout() {
        return sellerPayout;
    }

    public int saleTax() {
        return saleTax;
    }

    public int buyTax() {
        return buyTax;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
