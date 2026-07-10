# ASCII3D Lab

Una app Android nativa para modelar geometría 3D en una pantalla ASCII. Es como meter un mini Blender dentro de una terminal verde fosforescente, pero sin que el APK pese como una nevera.

## Qué tiene esta primera versión

- Render 3D ASCII con `Canvas`, sin motores externos.
- Primitivas: cubo, pirámide y esfera low-poly.
- Cámara orbital con arrastre.
- Zoom con gesto de pellizco.
- Extrusión de cara frontal.
- Modo edición para mover vértices arrastrando.
- Mutación orgánica de la malla.
- Cambio de paletas de caracteres ASCII.
- Exportación del snapshot ASCII usando el menú de compartir de Android.
- GitHub Action para generar un APK debug automáticamente.

## Estructura

```txt
ascii3d-modeler/
├─ app/
│  ├─ build.gradle
│  └─ src/main/
│     ├─ AndroidManifest.xml
│     ├─ java/com/mangoscam/ascii3d/
│     │  ├─ MainActivity.java
│     │  └─ AsciiModelerView.java
│     └─ res/values/styles.xml
├─ .github/workflows/build-apk.yml
├─ build.gradle
├─ settings.gradle
├─ gradle.properties
└─ README.md
```

## Construir el APK en GitHub

1. Crea una repo nueva en GitHub, por ejemplo `ascii3d-modeler`.
2. Sube estos archivos.
3. Entra en la pestaña **Actions**.
4. Ejecuta **Build debug APK**.
5. Descarga el artifact `ASCII3D-Lab-debug-apk`.
6. Instala `app-debug.apk` en tu móvil.

## Construir localmente

Con Android Studio: abre la carpeta del proyecto y ejecuta `app`.

Con terminal, teniendo Android SDK y Gradle instalados:

```bash
gradle :app:assembleDebug
```

El APK saldrá en:

```txt
app/build/outputs/apk/debug/app-debug.apk
```

## Controles

- Arrastrar: rotar cámara.
- Pellizcar: zoom.
- **Cubo / Pira / Esfera**: cambia la primitiva.
- **Extrude**: extruye la cara más frontal.
- **Mutar**: deforma la malla.
- **Editar**: activa edición de vértices. Toca cerca de un vértice y arrastra.
- **Chars**: cambia la paleta ASCII.
- **Share**: exporta el modelo como texto ASCII.

## Próximos pasos buenos

- Guardar y cargar proyectos `.ascii3d`.
- Añadir herramientas: cortar, bevel, duplicar, suavizar, simetría.
- Exportar OBJ simple desde la nube de vértices y caras.
- Modo “escultura ASCII” con pincel de empujar/tirar.
- Timeline para animaciones raras tipo criatura-terminal.
- Integración con tu universo visual: símbolos, glitches, texturas ASCII y export para pósters.
