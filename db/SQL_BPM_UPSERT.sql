-- ============================================================
-- Geolocalizacion: indices, UNIQUE de una fila por paciente y recorte de ancho
-- Ejecutar en el SQL Editor de Supabase
--   proyecto: mpmmdqnwsnjuzbsyigvq
--
-- CORRECCION (2026-10-02) del encabezado anterior:
--   El encabezado viejo afirmaba que paciente_run_p / run_p guardaban
--   ciphertext AES-GCM de ~96 hex. Eso es FALSO para geolocalizacion:
--   sus valores son RUN en texto plano (12 chars, p.ej. '15.456.789-K'), y
--   el backfill a SHA-256 ya lo hace SQL_MENSAJES_ADJUNTOS_CIFRADO.sql con
--   una guarda de forma. El ciphertext de 96 hex si existe, pero en
--   bpm.paciente_run_p (otra tabla, fuera de este script).
--
--   Por eso BackfillGeolocalizacionHash.java ya NO es necesario: no hay
--   ciphertext que descifrar en geolocalizacion. Queda como recurso por si
--   algun dia apareciera.
--
-- Orden correcto de despliegue:
--   1) SQL_MENSAJES_ADJUNTOS_CIFRADO.sql  (backfill a hash)
--   2) FASE 1 de este script              (indices + UNIQUE)
--   3) FASE 2 de este script              (recorte de ancho, opcional)
-- ============================================================


-- ###########################################################################
-- FASE 1 — segura. No toca valores.
-- ###########################################################################

BEGIN;

-- 1) Chequeo de duplicados. El UNIQUE de mas abajo falla si esto devuelve
--    filas. Correlo primero y borra el duplicado mas antiguo a mano.
--
--    SELECT paciente_run_p, COUNT(*) AS filas
--      FROM public.geolocalizacion
--     WHERE paciente_run_p IS NOT NULL
--     GROUP BY paciente_run_p
--    HAVING COUNT(*) > 1;
--
--    (El esquema real usa la PK 'id' en ambas caras; ya NO se cita
--     'id_geolocalizacion', que se verifico que no existe.)

-- 2) Indices de soporte. UbicacionService busca por (paciente_run_p, fecha
--    desc) y el historial se pagina por (run_p, fecha desc).
CREATE INDEX IF NOT EXISTS idx_geolocalizacion_paciente_run
    ON public.geolocalizacion (paciente_run_p);

CREATE INDEX IF NOT EXISTS idx_historial_geo_run_fecha
    ON public.historial_geolocalizacion (run_p, fecha DESC);

CREATE INDEX IF NOT EXISTS idx_historial_geo_fecha
    ON public.historial_geolocalizacion (fecha);

-- 3) UNIQUE de una fila actual por paciente.
--    UbicacionService ya hace find-then-update, pero eso tiene una ventana de
--    carrera: el APK envia cada 2 s por WiFi y cada 1,5 s en simulacion, y dos
--    peticiones simultaneas pueden insertar dos filas. El UNIQUE lo cierra.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_geolocalizacion_paciente_run_p'
    ) THEN
        ALTER TABLE public.geolocalizacion
            ADD CONSTRAINT uq_geolocalizacion_paciente_run_p UNIQUE (paciente_run_p);
    END IF;
END $$;

COMMIT;


-- ###########################################################################
-- FASE 2 — recorte de ancho. Ejecutar tras el backfill de la Fase 1.
--          Los valores ya son 64 hex, asi que el ALTER no trunca.
-- ###########################################################################

-- BEGIN;
--
-- ALTER TABLE public.geolocalizacion
--     ALTER COLUMN paciente_run_p TYPE varchar(64);
--
-- ALTER TABLE public.historial_geolocalizacion
--     ALTER COLUMN run_p TYPE varchar(64);
--
-- COMMIT;


-- ============================================================
-- Verificacion
-- ============================================================
-- Valores que aun NO son hash de 64 hex. Antes del backfill dara > 0, despues 0:
--   SELECT COUNT(*) FROM public.geolocalizacion
--    WHERE paciente_run_p !~ '^[0-9a-f]{64}$';
--   SELECT COUNT(*) FROM public.historial_geolocalizacion
--    WHERE run_p !~ '^[0-9a-f]{64}$';
--
-- Constraints aplicados:
--   SELECT conname FROM pg_constraint
--    WHERE conrelid = 'public.geolocalizacion'::regclass;


-- ============================================================
-- BPM: SIN CAMBIOS EN ESTA MIGRACION
-- ============================================================
-- bpm.paciente_run_p SI contiene ciphertext AES-GCM de 96 hex, escrito por el
-- ESP32 directo a Supabase por PostgREST (firmware fuera del repositorio).
-- Hashear esa columna ahora haria que el dispositivo siguiera mandando
-- ciphertext contra un UNIQUE, y las escrituras empezarian a fallar.
--
-- El script de BPM que si sigue siendo valido, por separado:
--   ALTER TABLE public.bpm
--     ADD CONSTRAINT uq_bpm_paciente_run_p UNIQUE (paciente_run_p);
--   ALTER TABLE public.bpm
--     ALTER COLUMN fecha SET DEFAULT now();
--   ALTER TABLE public.historial_bpm
--     ALTER COLUMN fecha SET DEFAULT now();
-- ============================================================
