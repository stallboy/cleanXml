package cleanXml;

import java.util.List;

public class Test {

    public static class Actor {
    }

    @SuppressWarnings("unused")
    public interface TargetRule {
        boolean eval(Actor self, Actor target);
    }

    public static class IsAlive implements TargetRule {
        @Override
        public boolean eval(Actor self, Actor target) {
            return true;
        }
    }

    @SuppressWarnings("unused")
    public static class IsInRange implements TargetRule {
        private final float min;
        private final float max;
        private final float maxSqr;
        private final float minSqr;

        public IsInRange(float min, float max) {
            this.min = min;
            this.max = max;
            minSqr = min * min;
            maxSqr = max * max;
        }

        @Override
        public boolean eval(Actor self, Actor target) {
            return true;
        }
    }

    public static class TargetSelector {
        private final String name;
        private final boolean ally;
        private final List<TargetRule> rules;

        public TargetSelector(String name, boolean ally, List<TargetRule> rules) {
            this.name = name;
            this.ally = ally;
            this.rules = rules;
        }

        @Override
        public String toString() {
            return String.format("name=%s, ally=%b, rules.size=%d", name, ally, rules.size());
        }
    }


    public static void main(String[] args) {
        XData xd = new XData("cleanXml");
        xd.register(TargetSelector.class);

        TargetSelector ts = new TargetSelector("enemy", false, List.of(
                new IsAlive(),
                new IsInRange(0, 10)));

        String xml = xd.toXmlString(ts);

        System.out.println(xml);

        TargetSelector ts2 = xd.fromXmlString(xml);
        System.out.println(ts2);
    }
}
