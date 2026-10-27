# ☀️ Integración Sunshine & Moonlight — USBAnywhere Android

🌐 **Idioma / Language:** **Español** | [English](SUNSHINE_MOONLIGHT_INTEGRATION_EN.md)

Este documento describe cómo automatizar la alternancia de control de mandos y volantes entre **Android TV / Google TV** y tu **PC Gaming con Sunshine & Moonlight**.

---

## 🎯 ¿Qué problema resuelve esta integración?

Al usar Moonlight en Android TV / Nvidia Shield:
1. **El secuestro del mando**: Si dejas el mando conectado por USB/IP al PC, cuando sales del juego o cuando estás en el menú de Android TV no puedes moverte por los menús de la tele porque el PC tiene el mando capturado.
2. **La molestia de conectar/desconectar**: Tener que abrir apps intermedias en la tele para habilitar o deshabilitar el servidor USB antes de cada partida arruina la experiencia de consola (*"Console-like Experience"*).

### 💡 La Solución de USBAnywhere:
USBAnywhere incorpora un **microservidor HTTP embebido (puerto 3242)** en Android. Cuando Sunshine arranca un juego en el PC, envía un webhook que pone el mando en modo **`STREAMING_PC`**. En cuanto cierras el juego o finalizas la sesión en Moonlight, Sunshine envía automáticamente otro webhook que restaura el mando a **`LOCAL_ANDROID_TV`**.

---

## 🚀 Método 1: Configuración Directa en Sunshine Web UI (Recomendado)

Sunshine incluye la opción **Command Preparations** tanto a nivel global como por aplicación individual.

1. Abre el panel web de Sunshine en tu PC: `https://localhost:47990`
2. Ve a la pestaña **Configuration** (o edita una aplicación concreta en **Applications**).
3. Localiza la sección **Command Preparations** y añade una nueva regla:

| Campo | Valor / Comando a Introducir |
|---|---|
| **Do Command** | `curl.exe -s --connect-timeout 2 -m 3 -X POST "http://%SUNSHINE_CLIENT_IP%:3242/api/stream/start"` |
| **Undo Command** | `curl.exe -s --connect-timeout 2 -m 3 -X POST "http://%SUNSHINE_CLIENT_IP%:3242/api/stream/stop"` |

> [!TIP]
> **Variable `%SUNSHINE_CLIENT_IP%`**: Sunshine inyecta automáticamente la IP del dispositivo cliente que se acaba de conectar con Moonlight. Por tanto, no necesitas fijar una IP estática; funciona incluso si la tablet o la tele cambian de IP en tu red.
> Si utilizas una versión antigua de Sunshine donde dicha variable no esté disponible, sustituye `%SUNSHINE_CLIENT_IP%` por la IP local de tu Android TV (ej. `192.168.1.150`).

---

## 🛠️ Método 2: Usando los Scripts Automatizados (.bat o .ps1)

Hemos incluido scripts preparados y protegidos contra cuelgues en el directorio `docs/scripts/` de este repositorio:

* [`docs/scripts/sunshine_usbanywhere_do.bat`](scripts/sunshine_usbanywhere_do.bat)
* [`docs/scripts/sunshine_usbanywhere_undo.bat`](scripts/sunshine_usbanywhere_undo.bat)
* [`docs/scripts/sunshine_usbanywhere_handoff.ps1`](scripts/sunshine_usbanywhere_handoff.ps1)

### En Sunshine Web UI:
* **Do Command**:
  ```cmd
  cmd /c "C:\Ruta\A\sunshine_usbanywhere_do.bat"
  ```
* **Undo Command**:
  ```cmd
  cmd /c "C:\Ruta\A\sunshine_usbanywhere_undo.bat"
  ```

---

## 📄 Método 3: Inyección Directa en `apps.json` de Sunshine

Si prefieres editar directamente el archivo de configuración `apps.json` de Sunshine (ubicado habitualmente en `C:\Program Files\Sunshine\config\apps.json`), puedes añadir la preparación a cualquier juego o al lanzador de escritorio:

```json
{
  "name": "Desktop (con USBAnywhere)",
  "output": "",
  "cmd": "",
  "prep-cmd": [
    {
      "do": "curl.exe -s --connect-timeout 2 -m 3 -X POST \"http://%SUNSHINE_CLIENT_IP%:3242/api/stream/start\"",
      "undo": "curl.exe -s --connect-timeout 2 -m 3 -X POST \"http://%SUNSHINE_CLIENT_IP%:3242/api/stream/stop\""
    }
  ]
}
```

---

## 🎮 Conmutador Rápido de Emergencia con el Mando

Si estás en medio de una partida o en el escritorio y necesitas recuperar el control de la tele sin tocar el teclado ni cerrar Moonlight:

* Mantén pulsado **`Guide + Select`** (o **`Back + Start`**) en tu mando durante **2.5 segundos**.
* La app de Android TV mostrará una notificación HUD instantánea:
  * `🎮 Control Local de Android TV Restaurado`
* Si vuelves a mantener la combinación 2.5 segundos:
  * `🚀 Mando Enviado al Streaming de Sunshine en PC`

---

## 🧪 Comprobación de Diagnóstico (Desde el PC)

Puedes verificar en cualquier momento que el microservidor de handoff de tu Android TV está respondiendo ejecutando en PowerShell o CMD:

```powershell
# Consultar estado actual
curl.exe http://<IP_DE_TU_ANDROID_TV>:3242/api/status

# Respuesta esperada:
# {"status":"ok","mode":"LOCAL_ANDROID_TV","title":"Local Android TV","port":3242}
```
