package ae2.client.render.bloom;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

final class BloomShader {

    private static final String VERTEX_SHADER = """
        #version 120
        
        varying vec2 textureCoords;
        
        void main() {
            gl_Position = gl_Vertex;
            textureCoords = gl_MultiTexCoord0.st;
        }
        """;

    private final int program;
    private final Object2IntMap<String> uniformLocations = new Object2IntOpenHashMap<>();

    BloomShader(String fragmentShader) {
        int vertex = compile(GL20.GL_VERTEX_SHADER, VERTEX_SHADER);
        int fragment = compile(GL20.GL_FRAGMENT_SHADER, fragmentShader);
        this.program = GL20.glCreateProgram();
        GL20.glAttachShader(this.program, vertex);
        GL20.glAttachShader(this.program, fragment);
        GL20.glLinkProgram(this.program);
        if (GL20.glGetProgrami(this.program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            throw new IllegalStateException(GL20.glGetProgramInfoLog(this.program, 32768));
        }
        GL20.glDeleteShader(vertex);
        GL20.glDeleteShader(fragment);
    }

    static boolean isSupported() {
        return OpenGlHelper.shadersSupported && OpenGlHelper.isFramebufferEnabled();
    }

    private static int compile(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader, 32768);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException(log);
        }
        return shader;
    }

    void use(Runnable uniforms) {
        GL20.glUseProgram(this.program);
        uniforms.run();
    }

    void release() {
        GL20.glUseProgram(0);
    }

    int uniformLocation(String name) {
        return this.uniformLocations.computeIfAbsent(name, key -> GL20.glGetUniformLocation(this.program, (String) key));
    }

    void delete() {
        GL20.glDeleteProgram(this.program);
        this.uniformLocations.clear();
    }
}
