# cleanXml

- xml object mapper,  simple and clean.
- inspired by XStream.

## init 
- javac -parameters

```jshelllanguage
XData xd = new XData("cleanXml");

xd.register(TargetSelector.class);
```

## object to xml

```jshelllanguage
TargetSelector ts = new TargetSelector("enemy", false, List.of(
                new IsAlive(),
                new IsInRange(0, 10)));

String xml = xd.toXmlString(ts);
```

```xml
<TargetSelector ally="false" name="enemy">
    <IsAlive/>
    <IsInRange max="10.0" min="0.0"/>
</TargetSelector>
```

## xml to object

```jshelllanguage
TargetSelector ts2 = xd.fromXmlString(xml);
```