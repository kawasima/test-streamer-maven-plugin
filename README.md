test-streamer-maven-plugin
==========================

Maven plugin for submitting tests to TestStreamer.

## How to switch from surefire to TestStreamer

Add this profile to your pom.xml

```xml
<profile>
  <id>test-streamer</id>
  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
        <configuration>
          <skipTests>true</skipTests>
        </configuration>
      </plugin>
      <plugin>
        <groupId>net.unit8.maven.plugins</groupId>
        <artifactId>test-streamer-maven-plugin</artifactId>
        <version>0.1.0-SNAPSHOT</version>
        <configuration>
          <testStreamerUrl>http://test-streamer.local:5000/test-shots</testStreamerUrl>
        </configuration>
        <executions>
          <execution>
            <goals><goal>submit</goal></goals>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</profile>
```

And run maven with the profile.


```shell
% mvn -Ptest-streamer test
```
