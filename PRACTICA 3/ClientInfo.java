import java.net.InetAddress;
import java.util.Objects;

public class ClientInfo {
    private final String username;
    private final InetAddress address;
    private final int port;

    public ClientInfo(String username, InetAddress address, int port) {
        this.username = username;
        this.address = address;
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public InetAddress getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClientInfo that = (ClientInfo) o;
        return port == that.port &&
                Objects.equals(username, that.username) &&
                Objects.equals(address, that.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, address, port);
    }

    @Override
    public String toString() {
        return username + "@" + address.getHostAddress() + ":" + port;
    }
}