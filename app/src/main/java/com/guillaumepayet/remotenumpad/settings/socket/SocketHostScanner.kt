/*
 * Remote Numpad - a numpad application on Android for PCs lacking one.
 * Copyright (C) 2016-2020 Guillaume Payet
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.guillaumepayet.remotenumpad.settings.socket

import android.content.Context
import android.net.wifi.WifiManager
import com.guillaumepayet.remotenumpad.connection.socket.SocketConnectionInterface
import kotlinx.coroutines.*
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import java.net.UnknownHostException

/**
 * This scanner can search IP addresses and test ports to find potential hosts with a Remote Numpad
 * Server running on them.
 *
 * @constructor Prepare the address range to scan
 * @param preferenceFragment The fragment which intends to start the scan and know its result
 **/
class SocketHostScanner(private val preferenceFragment: SocketPreferenceFragment) {

    private val hostAddressStart: String

    private val hosts = ArrayList<Pair<String, String>>()

    private var probeCount = 0


    init {
        val context = preferenceFragment.requireActivity().applicationContext
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val clientAddress = wifiManager.dhcpInfo.ipAddress

        hostAddressStart = (clientAddress and 0xFF).toString() + '.' +
                ((clientAddress shr 8) and 0xFF).toString() + '.' +
                ((clientAddress shr 16) and 0xFF).toString() + '.'
    }

    /**
     * Start to scan the IP addresses for a Remote Numpad Server.
     */
    fun scan() {
        probeCount = 256

        for (i in 0 until probeCount) {
            GlobalScope.launch(Dispatchers.IO) {
                val address = hostAddressStart + i
                val endpoint = InetSocketAddress(address, SocketConnectionInterface.PORT)
                val socket = Socket()

                try {
                    socket.connect(endpoint, 500)
                } catch (e: UnknownHostException) {
                } catch (e: IOException) {
                }

                if (socket.isConnected) {
                    socket.outputStream.writer().use { writer ->
                        socket.inputStream.reader().buffered().use { reader ->
                            try {
                                writer.write("name\n")
                                writer.flush()

                                val name = reader.readLine()
                                hosts.add(Pair(name, address))
                            } catch (e: SocketException) {
                                if (socket.isClosed)
                                    throw SocketClosedException()
                                else
                                    throw OtherSocketException()
                            }
                        }
                    }
                }

                decrementProbeCount()
                socket.close()
            }
        }
    }


    /**
     * Decrement the probe count and send all the found hosts to the [SocketPreferenceFragment] if
     * there are no more probes.
     */
    @Synchronized
    private suspend fun decrementProbeCount() = withContext(Dispatchers.Main) {
        probeCount--

        if (probeCount == 0) {
            if (preferenceFragment.isResumed)
                preferenceFragment.updateHosts(hosts)
        }
    }
}