# user-id

The stable identity of a person in this estate: `UserId`, the UUID security mints at registration
(`users.id`). It never changes and is never reused, which is what an e-mail address cannot promise.

```java
UserId id = UserId.of(claims.get("sub"));   // the access token's subject
```

E-mail is an attribute of the person and travels as its own claim (`email`); anything that
identifies, keys or authorises should hold a `UserId`. Pure Java, no dependencies.
