package com.contentgrid.gateway.collections;

import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.Expect;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.IIII_Result;

@JCStressTest
@Outcome(id = "0, 0, 1, 1", expect = Expect.ACCEPTABLE, desc = "We have normality.")
@Outcome(id = {"-1, 0, *, *", "0, -1, *, *"}, expect = Expect.FORBIDDEN, desc = "Exception on .add()")
@Outcome(id = {"0, 0, -1, .*", "0, 0, .*, -1"}, expect = Expect.FORBIDDEN, desc = "Read after write failed.")
@Outcome(expect = Expect.FORBIDDEN, desc = "Something went wrong")
@State
public class ConcurrentLookupTest {

    private final ConcurrentLookup<String, Object> map = new ConcurrentLookup<>(Object::toString/*, new NoopReadWriteLock()*/);

    @Actor
    public void actor1(IIII_Result r) {
        try {
            map.add("foo");
            r.r1 = 0;
        } catch (Exception e) {
            r.r1 = -1;
        }
    }

    @Actor
    public void actor2(IIII_Result r) {
        try {
            map.add("bar");
            r.r2 = 0;
        } catch (Exception e) {
            r.r2 = -1;
        }
    }

    @Arbiter
    public void arbiter(IIII_Result r) {
        var v1 = map.get("foo");
        var v2 = map.get("bar");
        r.r3 = ("foo".equals(v1)) ? 1 : -1;
        r.r4 = ("bar".equals(v2)) ? 1 : -1;
    }

}