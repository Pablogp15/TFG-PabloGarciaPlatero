import pandas as pd
from sklearn.model_selection import train_test_split
from sklearn.tree import DecisionTreeClassifier
from sklearn import metrics

# Cargar el historial de datos
historial = pd.read_csv("sensor_data_variado_horas.csv")  # Reemplaza con el archivo correspondiente

# Convertir 'timestamp' en tipo datetime
historial['fecha'] = pd.to_datetime(historial['timestamp'], errors='coerce')

# Verificar que la conversión fue exitosa
if historial['fecha'].isnull().any():
    raise ValueError("Error en la conversión de algunas fechas. Revisa el formato de 'timestamp' en el archivo CSV.")

# Función para determinar la estación basada en la fecha
def obtener_estacion(fecha):
    mes = fecha.month
    if mes in [12, 1, 2]:
        return "invierno"
    elif mes in [3, 4, 5]:
        return "primavera"
    elif mes in [6, 7, 8]:
        return "verano"
    else:
        return "otoño"

# Crear columna de estación
historial['estacion'] = historial['fecha'].apply(obtener_estacion)
historial['hora'] = historial['fecha'].dt.hour

# Entrenamiento de modelos
def train_decision_tree(data, feature, target):
    X = data[[feature, "hora", "estacion"]]
    y = data[target]
    
    # Convertir 'estacion' a valores numéricos
    X = pd.get_dummies(X, columns=['estacion'], drop_first=True)
    
    # Dividir en conjunto de entrenamiento y prueba
    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.3, random_state=42)
    
    # Inicializar y entrenar el modelo
    tree_model = DecisionTreeClassifier(random_state=42)
    tree_model.fit(X_train, y_train)
    
    # Evaluación
    y_pred = tree_model.predict(X_test)
    accuracy = metrics.accuracy_score(y_test, y_pred)
    print(f"Precisión del modelo para {target}: {accuracy:.2f}")
    
    return tree_model, X_train.columns

# Crear modelo para humedad
modelo_humedad, columnas_humedad = train_decision_tree(historial, "valor_humedad", "encender_humidificador")

# Crear modelo para pureza del aire
modelo_pureza_aire, columnas_pureza = train_decision_tree(historial, "valor_pureza_aire", "encender_purificador")

# Datos de prueba
prueba_datos = pd.DataFrame({
    'valor_humedad': [33, 50],       # niveles de humedad bajos y normales
    'valor_pureza_aire': [850, 500],  # pureza del aire alta y normal
    'hora': [12, 12],
    'estacion': ['invierno', 'verano']  # Especifica la estación
})

# Generar las variables dummy en los datos de prueba
prueba_datos_humedad = pd.get_dummies(prueba_datos[['valor_humedad', 'hora', 'estacion']], columns=['estacion'], drop_first=True)
prueba_datos_pureza = pd.get_dummies(prueba_datos[['valor_pureza_aire', 'hora', 'estacion']], columns=['estacion'], drop_first=True)

# Alinear las columnas para que coincidan con las del conjunto de entrenamiento
prueba_datos_humedad = prueba_datos_humedad.reindex(columns=columnas_humedad, fill_value=0)
prueba_datos_pureza = prueba_datos_pureza.reindex(columns=columnas_pureza, fill_value=0)

# Predicción de si el humidificador debería encenderse o no
predicciones_humidificador = modelo_humedad.predict(prueba_datos_humedad)

# Predicción de si el purificador debería encenderse o no
predicciones_purificador = modelo_pureza_aire.predict(prueba_datos_pureza)

# Guardar las predicciones en un CSV
resultados = pd.DataFrame({
    'encender_humidificador': predicciones_humidificador,
    'encender_purificador': predicciones_purificador
})
resultados.to_csv("predicciones_decision_tree.csv", index=False)

# Mostrar resultados
print("Predicciones guardadas en 'predicciones_decision_tree.csv'")
print(resultados)
