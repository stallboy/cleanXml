# cleanXml

- xml to object, object to xml mapper.  
- simple and clean.
- inspired by XStream

## init 

```java
XData xd = new XData("cleanXml");
xd.register(TargetSelector.class);
```

## object to xml

```java
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

```java
TargetSelector ts2 = xd.fromXmlString(xml);
```