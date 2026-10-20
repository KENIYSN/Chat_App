package com.chat.model;

import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.bson.types.ObjectId;

import java.util.Date;
import java.util.Objects;

/**
 * Modèle représentant une relation ou demande d'amitié (Collection 'friendships').
 *
 * <p>Statuts possibles :</p>
 * <ul>
 *   <li>{@code PENDING} : Demande envoyée, en attente d'acceptation par le destinataire.</li>
 *   <li>{@code ACCEPTED} : Demande acceptée, les deux utilisateurs sont désormais amis.</li>
 * </ul>
 */
public class Friendship {

    @BsonId
    private ObjectId id;

    @BsonProperty("sender")
    private String sender;

    @BsonProperty("receiver")
    private String receiver;

    @BsonProperty("status")
    private String status; // "PENDING" | "ACCEPTED"

    @BsonProperty("createdAt")
    private Date createdAt;

    public Friendship() {
        this.id = new ObjectId();
        this.createdAt = new Date();
        this.status = "PENDING";
    }

    public Friendship(String sender, String receiver, String status) {
        this();
        this.sender = sender;
        this.receiver = receiver;
        this.status = status;
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Friendship that = (Friendship) o;
        return Objects.equals(sender, that.sender) && Objects.equals(receiver, that.receiver);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sender, receiver);
    }
}
