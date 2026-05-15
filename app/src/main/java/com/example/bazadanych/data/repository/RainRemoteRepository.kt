package com.example.bazadanych.data.repository

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.bazadanych.data.db.FieldData
import com.example.bazadanych.data.db.FieldItem
import com.example.bazadanych.data.db.Rain
import com.example.bazadanych.data.db.RainAdvInt
import com.example.bazadanych.data.db.RainAdvUInt
import com.example.bazadanych.data.db.RainStatus
import com.example.bazadanych.data.db.UserSettings
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

class RainRemoteRepository {

    private val baseUrl = "https://rain-tech.pl/android/"
    private val client = OkHttpClient()
    private val TAG = "RainRepo"
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun postOnMain(action: () -> Unit) = mainHandler.post(action)

    fun getAgriculturalFields(email: String, callback: (List<FieldItem>) -> Unit) {
        val url = "${baseUrl}get_agricultural_fields.php?email=$email"
        client.newCall(Request.Builder().url(url).get().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { postOnMain { callback(emptyList()) } }
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                val list = mutableListOf<FieldItem>()
                try {
                    val jsonArray = JSONArray(body ?: "[]")
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        list.add(FieldItem(
                            id = obj.optInt("id", 0),
                            name = obj.optString("name", "Pole"),
                            areaHa = obj.optDouble("area_ha", 0.0),
                            cropType = obj.optString("crop_type", ""),
                            comment = obj.optString("comment", ""),
                            color = obj.optString("color", "#604CAF50"),
                            coordinates = obj.optString("coordinates", "")
                        ))
                    }
                } catch (e: Exception) { }
                postOnMain { callback(list) }
            }
        })
    }

    fun getRainAdvInt(id: String, email: String, callback: (RainAdvInt?) -> Unit) {
        val url = "${baseUrl}get_rain_adv_details.php?id=$id&email=$email"
        Log.d("RainRepo", "Wysyłam zapytanie do URL: $url")

        val request = Request.Builder().url(url).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Jeśli wejdzie tutaj, masz problem z uprawnieniami, internetem lub blokadą HTTP (Cleartext)
                Log.e("RainRepo", "Błąd sieci (onFailure): ${e.message}", e)
                postOnMain { callback(null) }
            }

            override fun onResponse(call: Call, response: Response) {
                Log.d("RainRepo", "Otrzymano odpowiedź HTTP Kod: ${response.code}")

                // 1. Sprawdzamy czy kod HTTP to 200-299
                if (!response.isSuccessful) {
                    Log.e("RainRepo", "Serwer zwrócił kod błędu HTTP: ${response.code}")
                    postOnMain { callback(null) }
                    return
                }

                val responseBody = response.body?.string()
                Log.d("RainRepo", "Surowa odpowiedź z serwera (JSON): $responseBody")

                if (responseBody.isNullOrEmpty()) {
                    Log.e("RainRepo", "Odpowiedź serwera jest pusta (null lub empty)")
                    postOnMain { callback(null) }
                    return
                }

                try {
                    val obj = JSONObject(responseBody)

                    // 2. Bezpieczniejsze sprawdzanie błędu zamiast .contains("error")
                    if (obj.has("error") || obj.optString("status") == "error") {
                        Log.e("RainRepo", "Serwer zwrócił błąd wewnątrz JSON: $responseBody")
                        postOnMain { callback(null) }
                        return
                    }

                    // Mapowanie JSON -> RainAdvInt
                    val data = RainAdvInt(
                        id = obj.optInt("id_rekordu"),
                        urzadzenieId = obj.optInt("urzadzenie_id"),
                        userEmail = obj.optString("user_email"),
                        daneStm = obj.optInt("dane_stm"),
                        czyPracuje = obj.optInt("czy_pracuje"),
                        opoznienieAktualizacji = obj.optInt("opoznienie_aktualizacji"),
                        predkoscZadana = obj.optInt("predkosc_zadana"),
                        opoznionyStartPracy = obj.optInt("opozniony_start_pracy") == 1,
                        opoznionyStartZwijania = obj.optInt("opozniony_start_zwijania") == 1,
                        opoznioneZakonczenie = obj.optInt("opoznione_zakonczenie") == 1,
                        podlewanieStrefowe = obj.optInt("podlewanie_strefowe") == 1,
                        rozpoczeciePracy = obj.optInt("rozpoczecie_pracy") == 1,
                        zwijanie = obj.optInt("zwijanie") == 1,
                        predkoscStrefa1 = obj.optInt("predkosc_strefa_1"),
                        predkoscStrefa2 = obj.optInt("predkosc_strefa_2"),
                        predkoscStrefa3 = obj.optInt("predkosc_strefa_3"),
                        czasStm = obj.optString("czas_stm"),
                        czasDodania = obj.optString("czas_dodania")
                    )

                    Log.d("TEST_DANYCH", "Sukces! Sparsowano obiekt: $data")
                    postOnMain { callback(data) }

                } catch (e: Exception) {
                    // Jeśli wejdzie tutaj, struktura JSON z PHP nie odpowiada mapowaniu optInt/optString
                    Log.e("TEST_DANYCH", "Błąd parsowania JSON: ${e.message}", e)
                    postOnMain { callback(null) }
                }
            }
        })
    }

    fun getUserSettings(email: String, callback: (UserSettings?) -> Unit) {
        val url = "${baseUrl}get_settings.php?email=$email"
        Log.d("RainRepo", "Pobieram ustawienia z: $url")

        val request = Request.Builder().url(url).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("RainRepo", "Błąd sieci: ${e.message}")
                postOnMain { callback(null) }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    postOnMain { callback(null) }
                    return
                }

                val responseBody = response.body?.string()
                if (responseBody.isNullOrEmpty()) {
                    postOnMain { callback(null) }
                    return
                }

                try {
                    val obj = JSONObject(responseBody)

                    if (obj.has("error")) {
                        postOnMain { callback(null) }
                        return
                    }

                    // Mapowanie ręczne tak jak lubisz
                    val data = UserSettings(
                        powiadomienia = obj.optInt("powiadomienia") == 1,
                        powiadomienie_A = obj.optInt("powiadomienie_A") == 1,
                        powiadomienie_B = obj.optInt("powiadomienie_B") == 1,
                        powiadomienie_C = obj.optInt("powiadomienie_C") == 1,
                        sms = obj.optInt("sms") == 1,
                        dzwonienie = obj.optInt("dzwonienie") == 1
                    )

                    postOnMain { callback(data) }

                } catch (e: Exception) {
                    Log.e("RainRepo", "Błąd parsowania ustawień: ${e.message}")
                    postOnMain { callback(null) }
                }
            }
        })
    }

    fun saveUserSettings(email: String, settings: Map<String, Boolean>, callback: (Boolean) -> Unit) {
        val url = "${baseUrl}save_settings.php"

        val formBuilder = FormBody.Builder()
            .add("email", email)

        // Dodajemy pola z mapy
        settings.forEach { (key, value) ->
            formBuilder.add(key, if (value) "1" else "0")
        }

        val request = Request.Builder()
            .url(url)
            .post(formBuilder.build())
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                postOnMain { callback(false) }
            }

            override fun onResponse(call: Call, response: Response) {
                val result = response.body?.string()?.trim()
                // Logika identyczna jak w Twoim saveWorker - sprawdzamy surowe "OK"
                postOnMain { callback(result == "OK") }
            }
        })
    }

    fun getRainAdvUInt(id: String, email: String, callback: (RainAdvUInt?) -> Unit) {
        val url = "${baseUrl}get_rain_adv_u_details.php?id=$id&email=$email"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                postOnMain { callback(null) }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                try {
                    if (!body.isNullOrEmpty() && !body.contains("error")) {
                        val obj = JSONObject(body)
                        val data = RainAdvUInt(
                            idLog = obj.optInt("id_log"),
                            urzadzenieId = obj.optInt("urzadzenie_id"),
                            userEmail = obj.optString("user_email"),
                            zrodloDanych = obj.optInt("zrodlo_danych"),
                            rozwiniecieAktualne = obj.optInt("rozwiniecie_aktualne"),
                            predkoscAktualna = obj.optInt("predkosc_aktualna"),
                            odlegloscDoKonca = obj.optInt("odleglosc_do_konca"),
                            pracaH = obj.optInt("praca_h"),
                            pracaMin = obj.optInt("praca_min"),
                            pracaS = obj.optInt("praca_s"),
                            doZwinieciaH = obj.optInt("do_zwiniecia_h"),
                            doZwinieciaMin = obj.optInt("do_zwiniecia_min"),
                            doZwinieciaS = obj.optInt("do_zwiniecia_s"),
                            strefa1Start = obj.optInt("strefa_1_start"),
                            strefa2Start = obj.optInt("strefa_2_start"),
                            strefa3Start = obj.optInt("strefa_3_start"),
                            opoznionyStartPracyH = obj.optInt("opozniony_start_pracy_h"),
                            opoznionyStartPracyMin = obj.optInt("opozniony_start_pracy_min"),
                            opoznionyStartPracyS = obj.optInt("opozniony_start_pracy_s"),
                            opoznionyStartZwijaniaH = obj.optInt("opozniony_start_zwijania_h"),
                            opoznionyStartZwijaniaMin = obj.optInt("opozniony_start_zwijania_min"),
                            opoznionyStartZwijaniaS = obj.optInt("opozniony_start_zwijania_s"),
                            opoznioneZakonczeniePracyH = obj.optInt("opoznione_zakonczenie_pracy_h"),
                            opoznioneZakonczeniePracyMin = obj.optInt("opoznione_zakonczenie_pracy_min"),
                            opoznioneZakonczeniePracyS = obj.optInt("opoznione_zakonczenie_pracy_s"),
                            czasStm = obj.optString("czas_stm"),
                            dataZapisu = obj.optString("data_zapis")
                        )
                        postOnMain { callback(data) }
                    } else { postOnMain { callback(null) } }
                } catch (e: Exception) { postOnMain { callback(null) } }
            }
        })
    }

    fun saveRainAdvInt(email: String, data: RainAdvInt, callback: (Boolean) -> Unit) {
        val formBody = FormBody.Builder()
            .add("email", email)
            .add("id_rekordu", data.id.toString())
            .add("urzadzenie_id", data.urzadzenieId.toString())
            .add("dane_stm", data.daneStm.toString())
            .add("czy_pracuje", data.czyPracuje.toString())
            .add("opoznienie_aktualizacji", data.opoznienieAktualizacji.toString())
            .add("predkosc_zadana", data.predkoscZadana.toString())
            .add("opozniony_start_pracy", if (data.opoznionyStartPracy) "1" else "0")
            .add("opozniony_start_zwijania", if (data.opoznionyStartZwijania) "1" else "0")
            .add("opoznione_zakonczenie", if (data.opoznioneZakonczenie) "1" else "0")
            .add("podlewanie_strefowe", if (data.podlewanieStrefowe) "1" else "0")
            .add("rozpoczecie_pracy", if (data.rozpoczeciePracy) "1" else "0")
            .add("zwijanie", if (data.zwijanie) "1" else "0")
            .add("predkosc_strefa_1", data.predkoscStrefa1.toString())
            .add("predkosc_strefa_2", data.predkoscStrefa2.toString())
            .add("predkosc_strefa_3", data.predkoscStrefa3.toString())
            .add("czas_stm", data.czasStm ?: "")
            .build()

        val request = Request.Builder()
            .url(baseUrl + "save_rain_adv_details.php")
            .post(formBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                postOnMain { callback(false) }
            }

            override fun onResponse(call: Call, response: Response) {
                val result = response.body?.string()?.trim()
                postOnMain { callback(result == "OK") }
            }
        })
    }

    fun saveRainAdvUInt(email: String, data: RainAdvUInt, callback: (Boolean) -> Unit) {
        val formBody = FormBody.Builder()
            .add("email", email)
            .add("id_log", data.idLog.toString())
            .add("urzadzenie_id", data.urzadzenieId.toString())
            .add("zrodlo_danych", data.zrodloDanych.toString())
            .add("rozwiniecie_aktualne", data.rozwiniecieAktualne.toString())
            .add("predkosc_aktualna", data.predkoscAktualna.toString())
            .add("odleglosc_do_konca", data.odlegloscDoKonca.toString())
            .add("praca_h", data.pracaH.toString())
            .add("praca_min", data.pracaMin.toString())
            .add("praca_s", data.pracaS.toString())
            .add("do_zwiniecia_h", data.doZwinieciaH.toString())
            .add("do_zwiniecia_min", data.doZwinieciaMin.toString())
            .add("do_zwiniecia_s", data.doZwinieciaS.toString())
            .add("strefa_1_start", data.strefa1Start.toString())
            .add("strefa_2_start", data.strefa2Start.toString())
            .add("strefa_3_start", data.strefa3Start.toString())
            .add("opozniony_start_pracy_h", data.opoznionyStartPracyH.toString())
            .add("opozniony_start_pracy_min", data.opoznionyStartPracyMin.toString())
            .add("opozniony_start_pracy_s", data.opoznionyStartPracyS.toString())
            .add("opozniony_start_zwijania_h", data.opoznionyStartZwijaniaH.toString())
            .add("opozniony_start_zwijania_min", data.opoznionyStartZwijaniaMin.toString())
            .add("opozniony_start_zwijania_s", data.opoznionyStartZwijaniaS.toString())
            .add("opoznione_zakonczenie_pracy_h", data.opoznioneZakonczeniePracyH.toString())
            .add("opoznione_zakonczenie_pracy_min", data.opoznioneZakonczeniePracyMin.toString())
            .add("opoznione_zakonczenie_pracy_s", data.opoznioneZakonczeniePracyS.toString())
            .add("czas_stm", data.czasStm ?: "")
            .build()

        val request = Request.Builder()
            .url(baseUrl + "save_rain_adv_u_details.php")
            .post(formBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                postOnMain { callback(false) }
            }
            override fun onResponse(call: Call, response: Response) {
                val result = response.body?.string()?.trim()
                postOnMain { callback(result == "OK") }
            }
        })
    }

    fun getStmUpdateInfo(id: String, email: String, callback: (czasStm: String?, opoznienieAktualizacji: String?) -> Unit)
    {
        val encodedEmail = URLEncoder.encode(email, "UTF-8")
        val url = "${baseUrl}get_stm_update_info.php?id=$id&email=$encodedEmail"

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("REPO_ERROR", "Błąd sieci: ${e.message}")
                postOnMain {
                    callback(null, null)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { res ->
                    if (!res.isSuccessful) {
                        Log.e("REPO_ERROR", "Błąd serwera: ${res.code}")
                        postOnMain {
                            callback(null, null)
                        }
                        return
                    }

                    val responseBody = res.body?.string()

                    try {
                        if (responseBody.isNullOrEmpty()) {
                            postOnMain {
                                callback(null, null)
                            }
                            return
                        }

                        val json = JSONObject(responseBody)

                        // Jeśli PHP zwrócił błąd
                        if (json.has("error")) {
                            Log.w(
                                "REPO_WARNING",
                                "Serwer zwrócił błąd: ${json.getString("error")}"
                            )
                            postOnMain {
                                callback(null, null)
                            }
                            return
                        }

                        val czasStm = json.optString("czas_stm", null)
                        val opoznienieAktualizacji =
                            json.optString("opoznienie_aktualizacji", null)

                        postOnMain {
                            callback(czasStm, opoznienieAktualizacji)
                        }

                    } catch (e: Exception) {
                        Log.e("JSON_ERROR", "Błąd parsowania: ${e.message}")
                        postOnMain {
                            callback(null, null)
                        }
                    }
                }
            }
        })
    }

    fun getRains(email: String, callback: (List<Rain>) -> Unit) {
        val url = "${baseUrl}get_rains_with_status.php?email=$email"
        Log.d(TAG, "Pobieram maszyny z URL: $url") // DODAJ TO

        val request = Request.Builder().url(url).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Błąd sieci: ${e.message}")
                postOnMain { callback(emptyList()) }
            }

            override fun onResponse(call: Call, response: Response) {
                val jsonResponse = response.body?.string()
                Log.d(TAG, "Odpowiedź serwera: $jsonResponse") // DODAJ TO

                val list = mutableListOf<Rain>()
                if (!jsonResponse.isNullOrEmpty()) {
                    try {
                        val jsonArray = JSONArray(jsonResponse)
                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            list.add(Rain(
                                id = obj.optString("id", ""),
                                name = obj.optString("name", "Maszyna"),
                                hoseLength = obj.optString("hose_length", "0"),
                                comment = obj.optString("comment", ""),
                                isWorking = obj.optInt("is_working", 0)
                            ))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Błąd parsowania: ${e.message}")
                    }
                }
                postOnMain { callback(list) }
            }
        })
    }

    fun getRainDetails(id: String, email: String, callback: (name: String, length: String, comment: String) -> Unit) {
        val url = "${baseUrl}get_rain_details.php?id=$id&email=$email"
        Log.d("RainRepoDetails", "---> Wywołuję URL: $url")

        client.newCall(Request.Builder().url(url).get().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("RainRepoDetails", "Błąd połączenia: ${e.message}")
                postOnMain { callback("", "", "") }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                Log.d("RainRepoDetails", "<--- Odpowiedź serwera: $body")

                try {
                    if (body.isNullOrEmpty() || body.contains("error")) {
                        Log.e("RainRepoDetails", "Serwer zwrócił błąd lub pustkę!")
                        postOnMain { callback("", "", "") }
                        return
                    }

                    val obj = JSONObject(body)
                    postOnMain {
                        callback(
                            obj.optString("name", ""),
                            obj.optString("hose_length", "0"),
                            obj.optString("comment", "")
                        )
                    }
                } catch (e: Exception) {
                    Log.e("RainRepoDetails", "Błąd parsowania JSON: ${e.message}")
                    postOnMain { callback("", "", "") }
                }
            }
        })
    }

    fun saveRain(email: String, rain: Rain, callback: (Boolean) -> Unit) {
        // Jeśli ID jest puste, wysyłamy "0", co dla PHP oznacza nową pozycję
        val finalId = if (rain.id.isEmpty()) "0" else rain.id

        val formBody = FormBody.Builder()
            .add("id", finalId)
            .add("name", rain.name)
            .add("length", rain.hoseLength) // Klucz "length" pasuje do $_REQUEST['length'] w PHP
            .add("comment", rain.comment)
            .add("email", email)
            .build()

        val request = Request.Builder()
            .url(baseUrl + "save_rain.php")
            .post(formBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("RainRepo", "Błąd połączenia: ${e.message}")
                callback(false)
            }

            override fun onResponse(call: Call, response: Response) {
                val result = response.body?.string()?.trim() ?: ""
                Log.d("RainRepo", "Odpowiedź serwera: $result")

                // Sprawdzamy czy odpowiedź zawiera OK
                callback(result.contains("OK"))
            }
        })
    }

    fun getRainHistory(rainId: String, email: String, callback: (List<RainStatus>) -> Unit) {
        // Dodajemy email do adresu URL
        val url = "${baseUrl}get_rain_history.php?rain_id=$rainId&email=$email"

        client.newCall(Request.Builder().url(url).get().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                postOnMain { callback(emptyList()) }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                val list = mutableListOf<RainStatus>()
                try {
                    val jsonArray = JSONArray(body ?: "[]")
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        list.add(RainStatus(
                            id = obj.optInt("id"),
                            rainId = obj.optString("rain_id"),
                            isWorking = obj.optInt("is_working"),
                            setSpeed = obj.optDouble("set_speed"),
                            currentSpeed = obj.optDouble("current_speed"),
                            currentDistance = obj.optDouble("current_distance"),
                            extension = obj.optDouble("extension"), // Pobieranie nowej danej
                            workTime = obj.optString("work_time", "00:00:00"), // Pobieranie nowej danej
                            timeToFinish = obj.optString("time_to_finish", "--:--"),
                            updatedAt = obj.optString("updated_at"),
                            lat = obj.optDouble("lat"),
                            lng = obj.optDouble("lng"),
                            signalStrength = obj.optInt("signal")
                        ))
                    }
                } catch (e: Exception) { e.printStackTrace() }
                postOnMain { callback(list) }
            }
        })
    }
    fun saveAgriculturalField(
        email: String, id: String, name: String, areaHa: Double,
        cropType: String, comment: String, color: String, coords: String,
        callback: (Boolean) -> Unit
    ) {
        val formBody = FormBody.Builder()
            .add("email", email)
            .add("id", id)
            .add("name", name)
            .add("area_ha", areaHa.toString())
            .add("crop_type", cropType)
            .add("comment", comment)
            .add("color", color)
            .add("coordinates", coords)
            .build()

        val request = Request.Builder()
            .url(baseUrl + "save_agricultural_field.php")
            .post(formBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) { callback(false) }
            override fun onResponse(call: Call, response: Response) {
                val result = response.body?.string()?.trim()
                callback(result == "OK")
            }
        })
    }



    fun deleteRain(id: String, callback: (Boolean) -> Unit) {
        val formBody = FormBody.Builder().add("id", id).build()
        val request = Request.Builder().url(baseUrl + "delete_rain.php").post(formBody).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { callback(false) }
            override fun onResponse(call: Call, response: Response) {
                val result = response.body?.string()?.trim()
                callback(result == "OK")
            }
        })
    }

    fun updateRainManualLocation(id: String, email: String, lat: Double, lng: Double, callback: (Boolean) -> Unit) {
        val url = baseUrl + "update_rain_location.php"

        // LOG 1: Sprawdzamy co wysyłamy
        Log.d("RainRepoDebug", "===> WYSYŁAM LOKALIZACJĘ:")
        Log.d("RainRepoDebug", "URL: $url")
        Log.d("RainRepoDebug", "ID: $id, Email: $email, Lat: $lat, Lng: $lng")

        val formBody = FormBody.Builder()
            .add("id", id)
            .add("email", email)
            .add("lat", lat.toString())
            .add("lng", lng.toString())
            .build()

        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // LOG 2: Błąd połączenia (np. brak internetu, zły URL)
                Log.e("RainRepoDebug", "!!! BŁĄD SIECI: ${e.message}")
                postOnMain { callback(false) }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()?.trim()

                // LOG 3: Surowa odpowiedź z serwera
                Log.d("RainRepoDebug", "<=== ODPOWIEDŹ SERWERA: '$responseBody'")

                if (responseBody == "OK") {
                    postOnMain { callback(true) }
                } else {
                    // LOG 4: Serwer odpisał, ale coś mu się nie spodobało
                    Log.w("RainRepoDebug", "Serwer nie zwrócił OK. Treść: $responseBody")
                    postOnMain { callback(false) }
                }
            }
        })
    }


    fun deleteAgriculturalField(email: String, id: String, callback: (Boolean) -> Unit) {
        val formBody = FormBody.Builder().add("email", email).add("id", id).build()
        val request = Request.Builder().url(baseUrl + "delete_agricultural_field.php").post(formBody).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { postOnMain { callback(false) } }
            override fun onResponse(call: Call, response: Response) {
                val result = response.body?.string()?.trim()
                postOnMain { callback(result == "OK") }
            }
        })
    }
}