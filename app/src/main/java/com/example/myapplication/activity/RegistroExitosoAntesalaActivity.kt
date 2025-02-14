package com.example.myapplication.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.R
import com.example.myapplication.model.EmbeddingsResponse
import com.example.myapplication.model.Log
import com.example.myapplication.service.SendDataToBackend
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RegistroExitosoAntesalaActivity: AppCompatActivity() {

    var nombre = ""
    var apellido = ""
    var dni = ""



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.registro_exitoso_antesala)

        val embeddingsResponse = intent.getSerializableExtra("embeddingsResponse") as? EmbeddingsResponse

//        val embeddingsResponse = intent.getSerializableExtra("embeddingsResponse", EmbeddingsResponse::class.java)
        if (embeddingsResponse != null) {
            nombre = embeddingsResponse.data.nombre
            apellido = embeddingsResponse.data.apellido
            val rol = embeddingsResponse.data.rol
            dni = embeddingsResponse.data.dni.toString()
            val lugares = embeddingsResponse.data.lugares.joinToString(", ")

            val textoTipoCuenta = findViewById<TextView>(R.id.tipo_cuenta_texto)
            textoTipoCuenta.text = "$rol"

            // NOMBRE Y APELLIDO --- Actualizar TextViews con el nombre y apellido
            val textoNombreUsuario = findViewById<TextView>(R.id.ingreso_exitoso)
            textoNombreUsuario.text = "$nombre $apellido \n"+"DNI:$dni"
            val textoLugares= findViewById<TextView>(R.id.lugares_text)
            textoLugares.text = lugares
        }
    }

    fun ingresoClick(view: View) {
        // Iniciar la actividad RegistroExitosoActivity
        val intent = Intent(this, IngresoEgresoExitosoActivity::class.java)
        intent.putExtra("action", "Ingreso")
        val formato = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
        val horario = formato.format(Date())
        val log = Log( horario,nombre,apellido,dni,"Ingresando","online")
        val logRequest = SendDataToBackend(applicationContext)
        logRequest.sendLog(log)
        startActivity(intent)
    }

    fun egresoClick(view: View){
        // Iniciar la actividad RegistroExitosoActivity
        val intent = Intent(this, IngresoEgresoExitosoActivity::class.java)
        intent.putExtra("action", "Egreso")
        val formato = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
        val horario = formato.format(Date())
        val log = Log( horario,nombre,apellido,dni,"Saliendo","online")
        val logRequest = SendDataToBackend(applicationContext)
        logRequest.sendLog(log)
        startActivity(intent)
    }


    fun rechazarClick(view: View) {

        val intent = Intent(applicationContext, RegistroDenegado2Activity::class.java)
        intent.putExtra("origen", "RegistroExitosoAntesalaRrHh")
        startActivity(intent)
    }


}
