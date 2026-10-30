import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

/** Gradle build compatibility for Windows hosts where AF_UNIX loopback is unavailable. */
public final class TcpAgent {
    public static void premain(String args, Instrumentation instrumentation) {
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override public byte[] transform(ClassLoader loader, String name, Class<?> type,
                                               ProtectionDomain domain, byte[] source) {
                if (!"sun/nio/ch/PipeImpl$Initializer".equals(name)) return null;
                byte[] bytes=source.clone();
                int matches=0;
                for(int i=0;i<bytes.length-6;i++) {
                    if((bytes[i]&255)==42 && (bytes[i+1]&255)==28 && (bytes[i+2]&255)==181 && (bytes[i+5]&255)==177) {
                        bytes[i+1]=3;matches++;
                    }
                }
                return matches==1?bytes:null;
            }
        });
    }
}
