# Registro de cambios

## No publicado

### Cambiado

- Se ajustó Docker Compose para tomar el nombre de la base de datos, el usuario y la contraseña de PostgreSQL desde `POSTGRES_DB`, `POSTGRES_USER` y `POSTGRES_PASSWORD` en `.env`, y pasar esos valores a la API.

### Pendiente de verificación

- No se pudo iniciar ni comprobar la conexión con PostgreSQL porque Docker Compose no está instalado en el entorno.