package com.contentgrid.gateway.security.authority;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.springframework.security.oauth2.core.ClaimAccessor;

@Value
@FieldNameConstants(level= AccessLevel.PRIVATE)
public class Actor implements Serializable {

    @NonNull
    ActorType type;
    @NonNull
    ClaimAccessor claims;
    Actor parent;

    public enum ActorType {
        USER,
        EXTENSION
    }

    // Because Actor is embedded in a GrantedAuthority.
    // It needs to be Serializable, so it can be stored in a session
    @Serial
    private void writeObject(java.io.ObjectOutputStream stream) throws IOException {
        stream.writeObject(type);
        stream.writeObject(claims.getClaims());
        stream.writeObject(parent);
    }

    @Serial
    @SneakyThrows({NoSuchFieldException.class, IllegalAccessException.class})
    private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
        var typeField = Actor.class.getDeclaredField(Fields.type);
        var claimsField = Actor.class.getDeclaredField(Fields.claims);
        var parentField = Actor.class.getDeclaredField(Fields.parent);

        typeField.setAccessible(true);
        claimsField.setAccessible(true);
        parentField.setAccessible(true);

        typeField.set(this, stream.readObject());
        claimsField.set(this, stream.readObject());
        parentField.set(this, stream.readObject());
    }
}
