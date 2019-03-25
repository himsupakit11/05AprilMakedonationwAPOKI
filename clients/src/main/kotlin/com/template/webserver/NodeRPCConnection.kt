package com.template.webserver

import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.RPCException
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.NetworkHostAndPort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean

private const val CORDA_USER_NAME = "config.rpc.username"
private const val CORDA_USER_PASSWORD = "config.rpc.password"
private const val CORDA_NODE_HOST = "config.rpc.host"
private const val CORDA_RPC_PORT = "config.rpc.port"

@SpringBootApplication
class NodeRPCConnection(
        @Value("\${$CORDA_NODE_HOST}") private val host: String,
        @Value("\${$CORDA_USER_NAME}") private val username: String,
        @Value("\${$CORDA_USER_PASSWORD}") private val password: String){

    @Bean
    fun rpcClient(): CordaRPCOps{
        log.info("Connecting to Corda on $host using username $username and password $password")
        var maxRetires = 100
        do{
            try{
                return  CordaRPCClient(NetworkHostAndPort.parse(host)).start(username,password).proxy
            } catch (ex: RPCException){
                if (maxRetires --> 0){
                    Thread.sleep(1000)
                } else{
                    throw ex
                }
            }
        } while (true)
    }

    companion object{
        private val log = LoggerFactory.getLogger(this::class.java)

        @JvmStatic
        fun main(args: Array<String>){
            SpringApplication.run(NodeRPCConnection::class.java,*args)
        }
    }

}




///**
// * Wraps an RPC connection to a Corda node.
// *
// * The RPC connection is configured using command line arguments.
// *
// * @param host The host of the node we are connecting to.
// * @param rpcPort The RPC port of the node we are connecting to.
// * @param username The username for logging into the RPC client.
// * @param password The password for logging into the RPC client.
// * @property proxy The RPC proxy.
// */
//@Component
//open class NodeRPCConnection(
//        @Value("\${$CORDA_NODE_HOST}") private val host: String,
//        @Value("\${$CORDA_USER_NAME}") private val username: String,
//        @Value("\${$CORDA_USER_PASSWORD}") private val password: String,
//        @Value("\${$CORDA_RPC_PORT}") private val rpcPort: Int): AutoCloseable {
//
//    lateinit var rpcConnection: CordaRPCConnection
//        private set
//    lateinit var proxy: CordaRPCOps
//        private set
//
//    @PostConstruct
//    fun initialiseNodeRPCConnection() {
//            val rpcAddress = NetworkHostAndPort(host, rpcPort)
//            val rpcClient = CordaRPCClient(rpcAddress)
//            val rpcConnection = rpcClient.start(username, password)
//            proxy = rpcConnection.proxy
//    }
//
//    @PreDestroy
//    override fun close() {
//        rpcConnection.notifyServerAndClose()
//    }
//}

