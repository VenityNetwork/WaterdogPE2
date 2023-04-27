/*
 * Copyright 2022 WaterdogTEAM
 * Licensed under the GNU General Public License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.waterdog.waterdogpe.network.serverinfo;

import dev.waterdog.waterdogpe.network.EventLoops;
import dev.waterdog.waterdogpe.network.connection.client.ClientConnection;
import dev.waterdog.waterdogpe.network.connection.codec.initializer.ProxiedClientSessionInitializer;
import dev.waterdog.waterdogpe.network.protocol.ProtocolVersion;
import dev.waterdog.waterdogpe.player.ProxiedPlayer;
import dev.waterdog.waterdogpe.utils.config.proxy.NetworkSettings;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import org.cloudburstmc.netty.channel.raknet.RakChannelFactory;
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption;

import java.net.InetSocketAddress;

public class BedrockServerInfo extends ServerInfo {

    public BedrockServerInfo(String serverName, InetSocketAddress address, InetSocketAddress publicAddress) {
        super(serverName, address, publicAddress);
    }

    @Override
    public ServerInfoType getServerType() {
        return ServerInfoType.BEDROCK;
    }

    @Override
    public Future<ClientConnection> createConnection(ProxiedPlayer player) {
        ProtocolVersion version = player.getProtocol();
        NetworkSettings networkSettings = player.getProxy().getNetworkSettings();

        // Just pick EventLoop here, and we can use it for our promise too
        EventLoop eventLoop = player.getProxy().getWorkerEventLoopGroup().next();
        Promise<ClientConnection> promise = eventLoop.newPromise();
        new Bootstrap()
                .channelFactory(RakChannelFactory.client(EventLoops.getChannelType().getDatagramChannel()))
                .group(eventLoop)
                .option(RakChannelOption.RAK_PROTOCOL_VERSION, version.getRaknetVersion())
                .option(RakChannelOption.RAK_ORDERING_CHANNELS, 1)
                .option(RakChannelOption.RAK_CONNECT_TIMEOUT, 30000L)
                .option(RakChannelOption.RAK_SESSION_TIMEOUT, 30000L)
                .option(RakChannelOption.RAK_MTU, networkSettings.getMaximumDownstreamMtu())
                .handler(new ProxiedClientSessionInitializer(player, this, promise))
                .connect(this.getAddress()).addListener((ChannelFuture future) -> {
                    if (!future.isSuccess()) {
                        promise.tryFailure(future.cause());
                        future.channel().close();
                    }
                });
        return promise;
    }
}
