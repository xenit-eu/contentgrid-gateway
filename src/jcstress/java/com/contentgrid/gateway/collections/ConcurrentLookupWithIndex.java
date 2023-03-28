package com.contentgrid.gateway.collections;

import com.contentgrid.gateway.collections.ConcurrentLookup.Lookup;
import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.Expect;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.IIII_Result;

@JCStressTest
@Outcome(id = "1, 1, 1, 1", expect = Expect.ACCEPTABLE, desc = "We have normality.")
@Outcome(id = {"-1, .*, .*, .*", ".*, -1, .*, .*"}, expect = Expect.FORBIDDEN, desc = "Exception on .add()")
@Outcome(id = {".*, .*, -1, .*", ".*, .*, .*, -1"}, expect = Expect.FORBIDDEN, desc = "Lookup using index failed.")
@Outcome(expect = Expect.FORBIDDEN, desc = "Something went wrong.")
@State
public class ConcurrentLookupWithIndex {
    private final ConcurrentLookup<String, Object> map = new ConcurrentLookup<>(Object::toString/*, new NoopReadWriteLock()*/);
    private final Lookup<Integer, Object> lookup;

    public ConcurrentLookupWithIndex() {
        this.lookup = this.map.createLookup(obj -> obj.toString().length());
    }

    @Actor
    public void actor1(IIII_Result r) {
        try {
            map.add("foo");
            r.r1 = 1;
        } catch (Exception e) {
            r.r1 = -1;
        }
    }

    @Actor
    public void actor2(IIII_Result r) {
        try {
            map.add("bar");
            r.r2 = 1;
        } catch (Exception e) {
            r.r2 = -1;
        }
    }

    @Arbiter
    public void arbiter(IIII_Result r) {
        var lookup = this.lookup.apply(3);

        r.r3 = (lookup != null && lookup.contains("foo")) ? 1 : -1;
        r.r4 = (lookup != null && lookup.contains("bar")) ? 1 : -1;
    }

}