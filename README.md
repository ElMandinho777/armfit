# ArmFit 1.2

App independiente para Wear OS (API 30 o superior), en Java con interfaz nativa. Incluye tus 32 ejercicios repartidos en Push, Pull, Pierna y Upper, y un día de descanso. No requiere teléfono, cuenta ni internet para entrenar.

## Abrir y compilar

Abre la carpeta `reloj` en Android Studio. Usa el JDK integrado (17 o superior) y sincroniza Gradle. Herramientas: SDK 35, Android Gradle Plugin 8.8.0, Gradle 8.10.2. Para probarla cuando quieras, selecciona tu emulador Wear OS Rectangular API 34 y pulsa Run. Esta versión no funciona en Apple Watch.

Comprobación sin abrir la app: `gradlew.bat assembleDebug testDebugUnitTest lintDebug`.

APK: `app/build/outputs/apk/debug/app-debug.apk`. Pruebas: `app/build/reports/tests/testDebugUnitTest/index.html`. Análisis Android: `app/build/reports/lint-results-debug.html`.

## Configuración general

El engrane de la esquina superior derecha del inicio reúne toda la configuración: series previstas (3 inicialmente), descanso (3 minutos inicialmente), vibración y sonido. El icono mide 18 dp, sin fondo circular permanente, dentro de una zona táctil de 48 dp en la parte superior derecha. Las series van de 1 a 50 y el descanso de 30 segundos a 15 minutos. Los valores se guardan y se aplican a todos los ejercicios de la próxima sesión.

Las series son una meta flexible: puedes terminar antes o hacer más. No hay configuración en las pantallas de ejercicio. Los avisos conservan sus preferencias anteriores; los ajustes antiguos por ejercicio se sustituyen por la configuración general para las sesiones nuevas. Una sesión que ya estaba iniciada conserva sus valores y progreso hasta terminarla.

## Entrenar con control manual

1. Elige el día, consulta la rutina y pulsa **Comenzar**.
2. Aparece **¿Con cuál empiezas?**. Selecciona cualquier ejercicio. Ninguno se inicia automáticamente; el tiempo de elección se contabiliza como tiempo entre ejercicios.
3. Realiza una serie y toca **Terminé la serie**. Se registra e inicia el descanso configurado.
4. Durante la cuenta solo aparecen **Pausar descanso** (o **Continuar descanso**) y **Saltar descanso**, sin botones de sumar o restar tiempo ni menús de configuración.
5. Al terminar o saltar, aparece **Descanso terminado** y se espera tu confirmación. Puedes iniciar la siguiente serie, terminar el ejercicio o **Descansar 30 s más**. Cuando concluyen esos 30 segundos puedes solicitar otros 30. Si cumpliste la meta, se ofrece terminar o hacer otra serie.
6. **Terminar ejercicio** requiere al menos una serie, no suma otra y permite elegir el siguiente ejercicio.

La vibración está inicialmente activada y el sonido desactivado. Los avisos se emiten al vencer el descanso con ArmFit abierta; si sales, el contador conserva su fecha de finalización y se comprueba al regresar. No hay alarma de fondo. El aviso no se repite cada segundo. Saltar el descanso abre la confirmación sin emitir un aviso de vencimiento. El emulador no permite sentir vibraciones; el sonido depende del volumen del sistema. La pantalla permanece encendida durante el descanso activo con la app visible.

## Máquina ocupada o cambio de orden

- **Hacer después / Cambiar:** deja el ejercicio pendiente de retomar, conserva sus series y pausa su tiempo y el descanso que quede. La lista permite escoger cualquier otro ejercicio.
- **Omitir por hoy:** pide confirmación y marca el ejercicio como omitido. Conserva cualquier serie o tiempo ya registrado, sin contarlo como completado.
- **Retomar:** tanto los ejercicios pospuestos como los completados u omitidos pueden retomarse. Sus series y tiempos se conservan. Un descanso pospuesto continúa con lo que quedaba; uno pausado manualmente sigue pausado.

Los estados son pendiente, en curso/pendiente de retomar, completado y omitido. No es necesario seguir el orden original. Antes de finalizar se muestran los pendientes y se permite volver a ellos. Incluso al completar todos los ejercicios, el usuario confirma el final de la sesión.

## Tiempo total y tiempo por ejercicio

El total cuenta desde **Comenzar** hasta confirmar **Finalizar y ver resumen**. Incluye descansos, ajustes, cambios y espera.

Cada ejercicio acumula tiempo solo mientras está seleccionado: series, descansos (incluidas sus pausas) y espera de confirmación. Al posponerlo, completarlo u omitirlo, su tiempo se detiene. Al retomarlo vuelve a sumar. 

La lista de selección no atribuye tiempo a ningún ejercicio. El resumen muestra este intervalo como **Tiempo entre ejercicios**. Por tanto, los tiempos por ejercicio más el tiempo entre ejercicios corresponden al total de la sesión (puede haber diferencias de segundos visibles por redondeo).

El resumen incluye todos los ejercicios, su estado, las series realizadas y el tiempo acumulado; los omitidos o pendientes no se confunden con completados. No hay historial: al volver al inicio se descarta ese resumen, pero se conservan las preferencias.

## Recuperación y actualización

La sesión actual se guarda y puede recuperarse al volver a abrir la app o recrear su pantalla. Al reiniciar completamente el dispositivo se descarta porque se reinicia el reloj monotónico. La versión 1.1 recupera las series y el estado de una sesión de la versión anterior. Como esa versión no medía el tiempo por ejercicio, no inventa un desglose: el tiempo previo queda sin atribuir y se avisa en el resumen.

## Archivos principales

- `Routines.java`: rutina original.
- `Session.java`: estados, tiempos, objetivos, descansos y cambios de ejercicio.
- `SessionStore.java`: sesión versionada y preferencias generales.
- `MainActivity.java`: pantallas, confirmaciones y avisos.
- `SessionTest.java`: pruebas de tiempos, series, pausa, confirmación y cambios de ejercicio.

## Revisión manual pendiente

La actualización se comprueba mediante compilación, pruebas unitarias y análisis estático, sin abrir la app. Al ejecutarla en el reloj, revisar: nombres largos y desplazamiento, engrane de configuración, selección inicial de ejercicio, aviso al llegar a cero, confirmación manual, máquina ocupada/retomar, omisión, cambios de orden y resumen de tiempos. El sonido requiere activar su opción y tener volumen audible.
