import pandas as pd
import random
from datetime import datetime, timedelta

# Función para variar los valores dentro de límites y ajustarlos
def vary_value_temp(previous_value, min_value, max_value):
    variation = random.choice([-0.5, 0, 0.5])  # Variación limitada
    new_value = previous_value + variation
    return max(min_value, min(new_value, max_value))

def vary_value_lum(previous_value, min_value, max_value):
    variation = random.choice([-100, -50, -25, 0, 25, 50, 100])
    new_value = previous_value + variation
    return max(min_value, min(new_value, max_value))

def vary_value_hum(previous_value, min_value, max_value):
    variation = random.choice([-10, -5, -2, 0, 2, 5, 10])
    new_value = previous_value + variation
    return max(min_value, min(new_value, max_value))

def vary_value_air(previous_value, min_value, max_value):
    variation = random.choice([-25, -5, 0, 5, 25])
    new_value = previous_value + variation
    return max(min_value, min(new_value, max_value))

# Función para randomizar los valores iniciales al comienzo de cada día y ajustarlos
def randomize_initial_values(season):
    temperature_ranges = {
        'winter': (15, 22),
        'spring': (18, 25),
        'summer': (22, 30),
        'autumn': (17, 24)
    }
    temp_min, temp_max = temperature_ranges.get(season, (18, 26))
    return {
        'temperature': round(random.uniform(temp_min, temp_max), 2),
        'luminosity': random.randint(100, 800),
        'humidity': round(random.uniform(30, 70), 1),
        'air_purity': random.randint(10, 200)
    }

# Determina la estación según la fecha
def get_season(date):
    month = date.month
    if month in [12, 1, 2]:
        return 'winter'
    elif month in [3, 4, 5]:
        return 'spring'
    elif month in [6, 7, 8]:
        return 'summer'
    elif month in [9, 10, 11]:
        return 'autumn'

# Ajustar la temperatura en función de la hora del día y la estación
def adjust_for_time_of_day_and_season(sensor_values, hour, season):
    # Definir tendencias según la estación
    season_temp_base = {
        'winter': 18,  # Temperatura base para invierno
        'spring': 22,  # Temperatura base para primavera
        'summer': 26,  # Temperatura base para verano
        'autumn': 20   # Temperatura base para otoño
    }
    base_temp = season_temp_base[season]

    # Ajustes horarios (más cálido al mediodía, más frío en la noche)
    if 8 <= hour <= 11:  # Mañana
        temp_adjust = -2 + (hour - 8) * 0.5
    elif 12 <= hour <= 18:  # Tarde
        temp_adjust = 2 - (18 - hour) * 0.3
    else:  # Noche
        temp_adjust = -3

    # Componente aleatorio limitado para evitar ruido excesivo
    random_adjust = random.uniform(-0.5, 0.5)

    # Calcular temperatura ajustada
    adjusted_temp = base_temp + temp_adjust + random_adjust
    sensor_values['temperature'] = max(15, min(adjusted_temp, 30))  # Limitar al rango [15, 30]

    return sensor_values

# Determinar temperatura de encendido del aire acondicionado (con variación)
def get_ac_temperature(season):
    ac_ranges = {
        'winter': (20, 24),
        'spring': (23, 26),
        'summer': (25, 28),
        'autumn': (21, 25)
    }
    ac_min, ac_max = ac_ranges.get(season, (23, 26))
    return round(random.uniform(ac_min, ac_max), 1)

# Determinar luminosidad de encendido de luces basada en la luminosidad exterior
def get_light_threshold(luminosity):
    if luminosity < 300:  # Luminosidad baja
        return random.randint(700, 800)  # Luz más alta
    elif 300 <= luminosity < 500:  # Luminosidad media-baja
        return random.randint(500, 700)  # Luz intermedia
    elif 500 <= luminosity < 700:  # Luminosidad media-alta
        return random.randint(300, 500)  # Luz moderada
    else:  # Luminosidad alta
        return random.randint(100, 300)  # Luz baja

# Generar el dataset
def generate_sensor_data(start_time, num_days, interval_hours=1):
    data = []
    current_time = start_time

    # Inicialización de las variables de estado
    humidificador_encendido = False
    purificador_encendido = False

    for _ in range(num_days):
        season = get_season(current_time)  # Determinar la estación
        sensor_values = randomize_initial_values(season)  # Valores iniciales según estación

        for hour in range(8, 19):  # De 8:00 a 18:00 (6:00 p.m.)
            current_time = current_time.replace(hour=hour, minute=0)
            sensor_values = adjust_for_time_of_day_and_season(sensor_values, hour, season)  # Ajustar por hora y estación

            # Variar los valores de los sensores
            sensor_values['temperature'] = vary_value_temp(sensor_values['temperature'], 15, 30)
            sensor_values['luminosity'] = vary_value_lum(sensor_values['luminosity'], 100, 800)
            sensor_values['humidity'] = vary_value_hum(sensor_values['humidity'], 30, 70)
            sensor_values['air_purity'] = vary_value_air(sensor_values['air_purity'], 20, 200)

            # Lógica para el humidificador
            if sensor_values['humidity'] < 35:
                humidificador_encendido = True
            elif sensor_values['humidity'] > 45:
                humidificador_encendido = False

            # Lógica para el purificador de aire
            if sensor_values['air_purity'] > 100:
                purificador_encendido = True
            elif sensor_values['air_purity'] < 50:
                purificador_encendido = False

            # Determinar los valores dinámicos de AC y luces
            ac_temperature = get_ac_temperature(season)
            light_threshold = get_light_threshold(sensor_values['luminosity'])  # Ajustar según luminosidad exterior

            # Crear el registro
            record = {
                'timestamp': current_time,
                'valor_temperatura_exterior': sensor_values['temperature'],
                'valor_luminosidad_exterior': sensor_values['luminosity'],
                'valor_humedad': sensor_values['humidity'],
                'valor_pureza_aire': sensor_values['air_purity'],
                'temperatura_aire_acondicionado': ac_temperature,
                'luminosidad_encendido_luces': light_threshold,
                'encender_humidificador': int(humidificador_encendido),
                'encender_purificador': int(purificador_encendido)
            }
            data.append(record)

        current_time += timedelta(days=1)

    return pd.DataFrame(data)

# Configuración para la generación de datos
start_time = datetime(2024, 9, 27, 8, 0)  # Fecha inicial
num_days = 365  # Cantidad de días

# Generar el dataset
df = generate_sensor_data(start_time, num_days)

# Guardar el dataset en un archivo CSV
df.to_csv('C:/UMA/4/TFG/sensor_data_completo_variado.csv', index=False)

print(f"Dataset generado con {len(df)} registros y guardado como 'sensor_data_completo_variado.csv'")
