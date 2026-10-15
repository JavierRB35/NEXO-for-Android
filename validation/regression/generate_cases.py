from pathlib import Path
from datetime import date,timedelta
rows=[]
def add(split,kind,inp,exp,note=''):
    assert '\t' not in inp+exp+note
    rows.append((split,kind,inp,exp,note))

# Fixed benchmark clock: 2026-08-23 17:15 (Sunday)
# TEMPORAL: dev phrasing and holdout phrasing intentionally differ.
temporal_dev=[
('mañana a las 10','2026-08-24|10:00'),('mañana a las 8:30','2026-08-24|08:30'),('pasado mañana a las 9','2026-08-25|09:00'),
('hoy a las 20:15','2026-08-23|20:15'),('esta mañana a las 9','2026-08-23|09:00'),('el lunes a las 11','2026-08-24|11:00'),
('el martes a las cinco de la tarde','2026-08-25|17:00'),('el miércoles a las 8','2026-08-26|08:00'),('el jueves a las 14:20','2026-08-27|14:20'),
('el viernes a las siete de la tarde','2026-08-28|19:00'),('el sábado a las 10:45','2026-08-29|10:45'),('el domingo a las 9','2026-08-23|09:00'),
('próximo domingo a las 9','2026-08-30|09:00'),('próximo lunes a las 12','2026-08-24|12:00'),('siguiente martes a las 18','2026-08-25|18:00'),
('25/08/2026 a las 08:15','2026-08-25|08:15'),('26-08-26 a las 9:30','2026-08-26|09:30'),('27.08.2026 a las 18:05','2026-08-27|18:05'),
('31/12 a las 23:00','2026-12-31|23:00'),('2/1 a las 10','2027-01-02|10:00'),('25 de agosto a las 8:15','2026-08-25|08:15'),
('veintiséis de agosto a las nueve y media','2026-08-26|09:30'),('treinta de agosto a las seis de la tarde','2026-08-30|18:00'),
('1 de septiembre a las 12','2026-09-01|12:00'),('dentro de 2 días a las 10','2026-08-25|10:00'),('en tres días a las 11','2026-08-26|11:00'),
('dentro de una semana a las 9','2026-08-30|09:00'),('en dos semanas a las 9','2026-09-06|09:00'),('dentro de un mes a las 10','2026-09-23|10:00'),
('a las 19:00','2026-08-23|19:00'),('a las 16:00','2026-08-24|16:00'),('a las seis y media de la tarde','2026-08-23|18:30'),
('a las ocho y cuarto de la noche','2026-08-23|20:15'),('a las diez menos cuarto de la noche','2026-08-23|21:45'),('a mediodía','2026-08-24|12:00'),
('a medianoche','2026-08-24|00:00'),('hoy durante toda la mañana a las 10','2026-08-23|10:00'),('martes veinticinco de agosto a las 8:15 de la mañana','2026-08-25|08:15'),
]
# repeat to get 75 varied context wrappers
wrappers=['Recuérdame {}','Tengo cita {}','Apunta {}','La reunión es {}','Necesito acordarme {}']
for i,(txt,exp) in enumerate(temporal_dev):
    add('dev','TEMP',txt,exp,'temporal base')
    if i<37:
        add('dev','TEMP',wrappers[i%len(wrappers)].format(txt),exp,'temporal con contexto')
# 75 total dev exact
# Holdout 25 with unseen wording/context
temporal_hold=[
('Quiero una cita mañana a las once y media','2026-08-24|11:30'),('Anota para pasado mañana a las 18:40','2026-08-25|18:40'),
('Nos vemos este jueves a las seis y cuarto de la tarde','2026-08-27|18:15'),('Reserva para el próximo viernes a las 20:30','2026-08-28|20:30'),
('Consulta el 29/08/2026 a las 10:10','2026-08-29|10:10'),('Evento 30-08-2026 a las 12:45','2026-08-30|12:45'),
('Revisión el 1 de septiembre a las nueve','2026-09-01|09:00'),('Cita el dos de septiembre a las cinco de la tarde','2026-09-02|17:00'),
('Avísame dentro de cuatro días a las 8','2026-08-27|08:00'),('Apunta en cinco días a las 21:15','2026-08-28|21:15'),
('Recuérdame dentro de tres semanas a las 10','2026-09-13|10:00'),('Evento en dos meses a las 11','2026-10-23|11:00'),
('Ponlo hoy a las 22:05','2026-08-23|22:05'),('Ponlo a las 18:00','2026-08-23|18:00'),('Ponlo a las 17:00','2026-08-24|17:00'),
('Cita el lunes a las nueve y media de la mañana','2026-08-24|09:30'),('Cita el miércoles a las siete y cuarto de la tarde','2026-08-26|19:15'),
('Evento el sábado a mediodía','2026-08-29|12:00'),('Evento el domingo a medianoche','2026-08-23|00:00'),
('Hoy a las diez de la mañana','2026-08-23|10:00'),('Mañana por la mañana a las 8:25','2026-08-24|08:25'),
('El 31.12.2026 a las 23:59','2026-12-31|23:59'),('El 01/01/27 a las 00:05','2027-01-01|00:05'),
('el martes 25 de agosto a las 9','2026-08-25|09:00'),('próximo sábado a las 13:30','2026-08-29|13:30')]
for x in temporal_hold:add('holdout','TEMP',x[0],x[1],'temporal holdout')

# LOCATION 50 dev + 20 holdout
loc_dev=[
('Cita mañana a las 10 en Vithas Valencia.','Vithas Valencia'),('Dentista el martes a las cinco en Clínica Dental Navarro.','Clínica Dental Navarro'),
('El jueves a las siete he quedado con Carlos en Nuevo Centro.','Nuevo Centro'),('Reunión en las oficinas de Sanz el jueves a las 11.','Oficinas de Sanz'),
('Comer el domingo en Casa Baldo.','Casa Baldo'),('Cita en Hospital La Fe, avenida Fernando Abril Martorell.','Hospital La Fe, avenida Fernando Abril Martorell'),
('Recoger paquete en Punto Pack de Ruzafa mañana.','Punto Pack de Ruzafa'),('Lugar: Nuevo Centro.','Nuevo Centro'),('Ubicación: Plaza del Ayuntamiento 1.','Plaza del Ayuntamiento 1'),
('Dirección: Calle Colón 25, Valencia.','Calle Colón 25, Valencia'),('Cita mañana en el Hospital Vithas Valencia a las 10.','Hospital Vithas Valencia'),
('Reunión mañana en Hotel Westin Valencia a las 9.','Hotel Westin Valencia'),('Comida en Restaurante Navarro el viernes.','Restaurante Navarro'),
('Café en Cafetería Lisboa mañana a las 10.','Cafetería Lisboa'),('Cita en Centro de Salud Ruzafa el lunes.','Centro de Salud Ruzafa'),
('Quedamos en Plaza de la Reina a las 12.','Plaza de la Reina'),('Nos vemos en Mercado de Colón mañana.','Mercado de Colón'),
('Reunión en sede central de Grupo Sanz a las 10.','Sede central de Grupo Sanz'),('Curso en aula 3 del centro cultural el martes.','Aula 3 del centro cultural'),
('Evento en Palacio de Congresos de Valencia el viernes.','Palacio de Congresos de Valencia'),
('Recuérdame en dos días llamar a Juan.',''),('Avísame en 30 minutos.',''),('Dímelo en voz alta.',''),('Guardar en Nexo.',''),('Pienso en Juan.',''),
('Cita en septiembre.',''),('Reunión en el momento que puedas.',''),('Lo haré en casa cuando pueda.',''),('Guárdalo en el cuaderno.',''),('Ponlo en la lista de compra.',''),
('Cita Centro: Hospital Vithas Valencia Dirección: Calle Vall de la Ballestera 59 Fecha: 26/08/2026 Hora: 09:30.','Hospital Vithas Valencia, Calle Vall de la Ballestera 59'),
('Lugar - Calle Xàtiva 24; Valencia','Calle Xàtiva 24'),('Ubicación - Estación Joaquín Sorolla; Valencia','Estación Joaquín Sorolla'),
('Reunión en la sede de la asociación a las 18.','Sede de la asociación'),('Quedamos en casa de Ana a las 20.','Casa de Ana'),
('Visita en museo de Bellas Artes el domingo.','Museo de Bellas Artes'),('Partido en Mestalla el sábado.','Mestalla'),
('Cita en Vithas 9 de Octubre a las 12.','Vithas 9 de Octubre'),('Revisión en IMED Valencia el martes.','IMED Valencia'),
('Comida en La Pepica el domingo.','La Pepica'),('Entrega en oficina de Correos Ruzafa mañana.','Oficina de Correos Ruzafa'),
('Clase en academia Berlitz el jueves.','Academia Berlitz'),('Reunión en coworking Wayco a las 9.','Coworking Wayco'),
('Consulta en clínica Quirón Valencia a las 16.','Clínica Quirón Valencia'),('Cita en ambulatorio de Monteolivete a las 8.','Ambulatorio de Monteolivete'),
('Evento en auditorio Roig Arena el viernes.','Auditorio Roig Arena'),('Quedamos en puerta principal del Bioparc a las 10.','Puerta principal del Bioparc'),
('Recuérdame pensar en el presupuesto.',''),('En una semana revisar el seguro.',''),('En vacaciones quiero descansar.','')]
for x in loc_dev:add('dev','LOC',x[0],x[1],'ubicación')
loc_hold=[
('Mañana tengo que estar en Vithas Valencia a las nueve.','Vithas Valencia'),('Nos encontramos en Nuevo Centro a las 18:30.','Nuevo Centro'),
('La consulta será en Clínica Baviera Valencia el jueves.','Clínica Baviera Valencia'),('La reunión es en las oficinas centrales de Sanz a las once.','Oficinas centrales de Sanz'),
('Cena el sábado en Casa Montaña.','Casa Montaña'),('Recoge el coche en Parking Centro Histórico mañana.','Parking Centro Histórico'),
('Visita en Oceanogràfic Valencia a las 10.','Oceanogràfic Valencia'),('Curso en Universidad de Valencia, campus Tarongers.','Universidad de Valencia, campus Tarongers'),
('Lugar: Hospital General Universitario de Valencia','Hospital General Universitario de Valencia'),('Dirección: Avenida del Cid 90, Valencia','Avenida del Cid 90, Valencia'),
('Recuérdame en tres semanas renovar el carnet.',''),('Avísame en una hora de apagar el horno.',''),('Piensa en la reunión de mañana.',''),('Guarda esto en notas.',''),
('Lo compraré en dos días.',''),('Cita en octubre.',''),('Hablar en público el viernes.',''),('Añade leche en la lista de compra.',''),
('Recordatorio Centro: Hospital La Fe Dirección: Avenida Fernando Abril Martorell 106 Fecha: 30/08/2026 Hora: 10:00.','Hospital La Fe, Avenida Fernando Abril Martorell 106'),
('Nos vemos en estación del Norte a las 8.','Estación del Norte')]
for x in loc_hold:add('holdout','LOC',x[0],x[1],'ubicación holdout')

# ROUTING: 30 dev + 10 holdout
route_dev=[
('Pon una alarma a las 7','alarm'),('Despiértame mañana a las 8','alarm'),('Programa una alarma a las 6:30','alarm'),('El despertador a las nueve','alarm'),
('Guarda un pensamiento: hoy ha sido un buen día','journal'),('Anota una reflexión sobre el tiempo','journal'),('Apunta una frase que dice vive el presente','journal'),('Pensamiento aprender a decir no','journal'),
('Tomar Losartán 50 mg a las 8','medication'),('Medicamento Metformina 850 mg a las 20','medication'),('Una pastilla de Eliquis 5 mg a las 9','medication'),('Tomarme antibiótico 500 mg cada 8 horas','medication'),
('Añade leche y huevos a la lista de la compra','shopping'),('Comprar pan y café','shopping'),('Quita azúcar de la lista de compra','shopping'),('Ya he comprado los huevos','shopping'),
('He aparcado aquí','parking'),('Guardar aparcamiento','parking'),('Dónde he aparcado','parking'),('Aparqué el coche aquí','parking'),
('Recuérdame llamar a Juan mañana','capture'),('Cita con el médico mañana a las 10','capture'),('Tengo que pagar el agua','capture'),('Reunión el martes a las 11','capture'),
('Guarda una nota sobre el seguro','capture'),('Quiero comprar una televisión','capture'),('Cena en Casa Baldo el viernes','capture'),('Cumpleaños de Ana el martes','capture'),
('Añade una tarea para revisar el coche','capture'),('Apunta cita con dentista','capture')]
for x in route_dev:add('dev','ROUTE',x[0],x[1],'enrutado')
route_hold=[
('Crea una alarma para las 7:15','alarm'),('Ponme una alarma a las ocho','alarm'),('Guárdame una reflexión sobre lo aprendido','journal'),('Frase: disfruta del camino','journal'),
('Debo tomar amoxicilina 500 mg cada 8 horas','medication'),('Añádeme mantequilla y pan en la lista de compra','shopping'),('Borra el café de la lista de la compra','shopping'),
('Estoy aparcado aquí','parking'),('Recuérdame la cita del martes','capture'),('Evento en Valencia mañana','capture')]
for x in route_hold:add('holdout','ROUTE',x[0],x[1],'enrutado holdout')

# CLASSIFICATION: 45 dev + 15 holdout
class_dev=[
('Cita con el cardiólogo mañana a las 10','event|Salud'),('PET-TAC en Hospital Vithas el martes','event|Salud'),('Analítica de sangre el lunes','event|Salud'),('Dentista el jueves a las 17','event|Salud'),
('Cumpleaños de Ana el 3 de septiembre','event|Cumpleaños'),('Cumple de Carlos mañana','event|Cumpleaños'),
('Recuérdame llamar a Juan mañana','reminder|Recordatorios'),('Avísame de pagar el recibo mañana','reminder|Finanzas'),('Acuérdate de llevar el coche al taller','reminder|Coche'),
('Comprar detergente','task|Compras'),('Tengo que llamar al fontanero','task|Tareas'),('Hay que enviar el documento','task|Documentos'),('Llevar el coche al taller','task|Coche'),
('Renovar el seguro del coche','expiry|Coche'),('El seguro vence el 1 de octubre','expiry|Coche'),('Caduca la garantía en noviembre','expiry|Documentos'),
('Reserva de hotel en Madrid','note|Viajes'),('Vuelo a Roma el viernes','note|Viajes'),('Comer en Restaurante Navarro','restaurant|Restaurantes'),('Cena en bar Ricardo','restaurant|Restaurantes'),
('Factura de electricidad','document|Documentos'),('Contrato de alquiler','document|Documentos'),('Pago del impuesto de circulación','note|Finanzas'),('Ingreso de la pensión','note|Finanzas'),
('Revisión de la caldera','note|Casa'),('Cambiar filtro del aire acondicionado','note|Casa'),('Idea para reorganizar el salón','note|Ideas'),('Se me ocurre hacer un viaje','note|Viajes'),
('ITV del coche el martes','note|Coche'),('Cambio de aceite del coche','note|Coche'),('Farmacia comprar medicación','task|Farmacia'),
('Tomar Losartán 50 mg a las 8','medication|Medicamentos'),('Medicamento Eliquis 5 mg a las 20','medication|Medicamentos'),
('Reunión de trabajo mañana a las 9','event|Otros'),('Consulta en el hospital mañana','event|Salud'),('Evento en el auditorio','event|Otros'),
('Comprar billetes para el viaje','task|Viajes'),('Pedir cita al médico','event|Salud'),('Solicitar presupuesto de seguro','task|Coche'),
('Documento PDF de la garantía','document|Documentos'),('Manual de la lavadora','document|Documentos'),('Quiero recordar una idea','reminder|Ideas'),
('Avisarme por voz de la cita','reminder|Recordatorios'),('Recordatorio hablado para llamar a Ana','reminder|Recordatorios'),('Cita médica en centro de salud','event|Salud')]
for x in class_dev:add('dev','CLASS',x[0],x[1],'clasificación')
class_hold=[
('Consulta de medicina nuclear el miércoles','event|Salud'),('Cita para radiodiagnóstico mañana','event|Salud'),('Cumpleaños de Marta el domingo','event|Cumpleaños'),
('Recuérdame pagar la hipoteca','reminder|Finanzas'),('Avísame de la revisión del coche','reminder|Coche'),('Tengo que recoger un paquete','task|Tareas'),
('Comprar café y azúcar','task|Compras'),('Renovación del seguro del coche','expiry|Coche'),('Vencimiento de contrato el mes que viene','expiry|Documentos'),
('Hotel para vacaciones','note|Viajes'),('Cenar en cafetería Lisboa','restaurant|Restaurantes'),('Garantía de frigorífico','document|Documentos'),
('Idea para una nueva lista','note|Ideas'),('Tomarme Metformina 850 mg a las 20','medication|Medicamentos'),('Reunión con Pedro el jueves','event|Otros')]
for x in class_hold:add('holdout','CLASS',x[0],x[1],'clasificación holdout')

# RECURRENCE 25 dev + 10 holdout
rec_dev=[
('cada día','daily'),('todos los días','daily'),('diariamente','daily'),('periodicidad diaria','daily'),('cada semana','weekly'),('todas las semanas','weekly'),('semanalmente','weekly'),
('cada mes','monthly'),('todos los meses','monthly'),('mensualmente','monthly'),('cada dos meses','bimonthly'),('cada 2 meses','bimonthly'),('bimestral','bimonthly'),('bimensual','bimonthly'),
('cada tres meses','quarterly'),('cada 3 meses','quarterly'),('trimestral','quarterly'),('cada seis meses','semiannual'),('cada 6 meses','semiannual'),('semestral','semiannual'),
('cada año','annual'),('todos los años','annual'),('anualmente','annual'),('sin repetir','none'),('solo una vez','none')]
for x in rec_dev:add('dev','REC',x[0],x[1],'recurrencia')
rec_hold=[('Recuérdame esto cada día','daily'),('Cita todas las semanas','weekly'),('Pago todos los meses','monthly'),('Revisión bimensual','bimonthly'),('Control trimestral','quarterly'),('Revisión semestral','semiannual'),('Cumpleaños anualmente','annual'),('Evento cada año','annual'),('Nota sin periodicidad','none'),('Una sola vez mañana','none')]
for x in rec_hold:add('holdout','REC',x[0],x[1],'recurrencia holdout')

# LISTS 38 dev + 12 holdout. Exact individualization is the primary behavior.
list_dev=[
('Añade huevos y mantequilla y leche y pan a la lista de la compra','add|huevos;mantequilla;leche;pan'),
('Añade leche, huevos, pan y café a la lista de compra','add|leche;huevos;pan;café'),('Comprar arroz y pasta y tomate','add|arroz;pasta;tomate'),
('Guárdame en la lista de la compra agua y azúcar y café','add|agua;azúcar;café'),('Apúntame pan y jamón y queso','add|pan;jamón;queso'),
('Pon leche desnatada y huevos camperos y pan integral','add|leche desnatada;huevos camperos;pan integral'),('Añade dos botellas de agua y un paquete de arroz','add|dos botellas de agua;un paquete de arroz'),
('Añade 2 kg de patatas y 1 kg de cebollas','add|2 kg de patatas;1 kg de cebollas'),('Añade coca cola y café y yogures','add|coca cola;café;yogures'),
('Quita azúcar de la lista de compra','remove|azúcar'),('Elimina leche y pan','remove|leche;pan'),('Borra café y galletas de la lista','remove|café;galletas'),
('Ya he comprado los huevos','check|huevos'),('He comprado leche y pan','check|leche;pan'),('Marca como comprado café y azúcar','check|café;azúcar'),
('Todavía no he comprado el pan','uncheck|pan'),('No he comprado leche y huevos','uncheck|leche;huevos'),('Desmarca café','uncheck|café'),
('Cambia leche por leche desnatada','replace|'),('Sustituye pan por pan integral','replace|'),('Elimina lo comprado','clear_checked|'),('Borra los comprados','clear_checked|'),
('Añade detergente y suavizante y lejía','add|detergente;suavizante;lejía'),('Añade champú y gel y desodorante','add|champú;gel;desodorante'),
('Añade pollo y pescado y carne','add|pollo;pescado;carne'),('Añade manzanas y plátanos y naranjas','add|manzanas;plátanos;naranjas'),
('Añade lentejas y garbanzos y atún','add|lentejas;garbanzos;atún'),('Añade harina y sal y aceite','add|harina;sal;aceite'),
('Necesito comprar leche y huevos','add|leche;huevos'),('Quiero comprar pan y queso','add|pan;queso'),('Compra arroz y pollo','add|arroz;pollo'),
('Añádeme yogur y cereales','add|yogur;cereales'),('Agrégame tomate y lechuga','add|tomate;lechuga'),('Méteme café y azúcar','add|café;azúcar'),
('Añade a la lista de la compra macarrones y atún','add|macarrones;atún'),('En la lista de la compra añade leche y pan','add|leche;pan'),
('Añade leche y pan para la compra','add|leche;pan'),('Quita el azúcar y el café','remove|azúcar;café')]
for x in list_dev:add('dev','LIST',x[0],x[1],'lista compra')
list_hold=[
('Añádeme mantequilla y pan y huevos en la lista de compra','add|mantequilla;pan;huevos'),('Pon en la lista de la compra café y leche y azúcar','add|café;leche;azúcar'),
('Apunta arroz, tomate y atún para la compra','add|arroz;tomate;atún'),('Necesito comprar detergente y suavizante','add|detergente;suavizante'),
('Quita leche y huevos de la lista de la compra','remove|leche;huevos'),('Borra el pan de la lista','remove|pan'),('He comprado arroz y pasta','check|arroz;pasta'),
('Marca comprados huevos y leche','check|huevos;leche'),('Aún no he comprado el café','uncheck|café'),('Desmarca pan y queso','uncheck|pan;queso'),
('Quita los comprados','clear_checked|'),('Añade dos cajas de leche y tres botellas de agua','add|dos cajas de leche;tres botellas de agua')]
for x in list_hold:add('holdout','LIST',x[0],x[1],'lista holdout')

# ALARM 20 dev + 8 holdout
alarm_dev=[
('Pon una alarma a las 7','true|true|07:00|false'),('Despiértame a las 8:30','true|true|08:30|false'),('Programa una alarma a las seis y media','true|true|06:30|false'),
('Alarma a las 22:15','true|true|22:15|false'),('Pon una alarma todos los días a las 7','true|true|07:00|true'),('Alarma de lunes a viernes a las 6:45','true|true|06:45|true'),
('Despertador fin de semana a las 9','true|true|09:00|true'),('Pon una alarma los lunes a las 8','true|true|08:00|true'),('Pon una alarma los martes y jueves a las 7','true|true|07:00|true'),
('Crea una alarma a las 10','true|true|10:00|false'),('Programa una alarma a mediodía','true|true|12:00|false'),('Pon alarma a medianoche','true|true|00:00|false'),
('Recuérdame llamar a Juan a las 8','false|false||false'),('Cita mañana a las 8','false|false||false'),('Tengo que levantarme a las 7','false|false||false'),
('Pon una alarma','true|false||false'),('Despertador','true|false||false'),('Alarma mañana a las nueve','true|true|09:00|false'),
('Despiértame el lunes a las 7','true|true|07:00|false'),('Pon una alarma cada día a las 6','true|true|06:00|true')]
for x in alarm_dev:add('dev','ALARM',x[0],x[1],'alarma')
alarm_hold=[
('Ponme una alarma a las 7:20','true|true|07:20|false'),('Crea una alarma de lunes a viernes a las siete','true|true|07:00|true'),
('Despiértame todos los días a las 8','true|true|08:00|true'),('Alarma los sábados y domingos a las 9:30','true|true|09:30|true'),
('Necesito un recordatorio a las 8','false|false||false'),('Pon una alarma mañana','true|false||false'),('Despertador a las seis y cuarto','true|true|06:15|false'),('Programa alarma a las 18:50','true|true|18:50|false')]
for x in alarm_hold:add('holdout','ALARM',x[0],x[1],'alarma holdout')

# MEDICATION 20 dev + 7 holdout
med_dev=[
('Tomar Losartán 50 mg a las 8','true|Losartán|50 mg'),('Tomarme Metformina 850 mg a las 20','true|Metformina|850 mg'),('Tomar Eliquis 5 mg a las 9','true|Eliquis|5 mg'),
('Medicamento Omeprazol 20 mg a las 8','true|Omeprazol|20 mg'),('Tomar amoxicilina 500 mg cada 8 horas','true|Amoxicilina|500 mg'),('Una pastilla Paracetamol 1 g a las 10','true|Paracetamol|1 g'),
('Tomar vitamina D 1 cápsula a las 9','true|Vitamina D|1 cápsula'),('Tomar jarabe 10 ml a las 8','true|Jarabe|10 ml'),('Medicacion: Sintrom 4 mg a las 20','true|Sintrom|4 mg'),
('Tomar ibuprofeno 600 mg cada 12 horas','true|Ibuprofeno|600 mg'),('Tomar antibiótico 500 mg a las 8','true|Antibiótico|500 mg'),('Una dosis de 5 mg a las 9','true|Una Dosis De|5 mg'),
('Comprar Losartán 50 mg','false|Comprar Losartán|50 mg'),('Cita con el médico a las 8','false|Cita Con El Médico|'),('Tomar café a las 8','false|Café|'),('Tomar agua a las 9','false|Agua|'),
('Pastilla de Eliquis 5 mg','true|Eliquis|5 mg'),('Medicamento: Crestor 20 mg','true|Crestor|20 mg'),('Dosis 2 comprimidos a las 8','true|Dosis|2 comprimidos'),('Tomar Prednisona 30 mg durante 5 días a las 9','true|Prednisona|30 mg')]
for x in med_dev:add('dev','MED',x[0],x[1],'medicación')
med_hold=[
('Debo tomar Losartán 50 mg a las 8','true|Losartán|50 mg'),('Me toca tomar Eliquis 5 mg a las 20','true|Eliquis|5 mg'),('Tomarme amoxicilina 500 mg cada ocho horas','true|Amoxicilina|500 mg'),
('Medicamento Metformina 850 mg a las 9','true|Metformina|850 mg'),('Comprar paracetamol 1 g','false|Comprar Paracetamol|1 g'),('Tomar un té a las 9','false|Un Té|'),('Cápsula vitamina D 1 cápsula a las 10','true|Vitamina D|1 cápsula')]
for x in med_hold:add('holdout','MED',x[0],x[1],'medicación holdout')

# Ensure exact target count and stable shuffle-free data.
print('cases',len(rows),'dev',sum(r[0]=='dev' for r in rows),'holdout',sum(r[0]=='holdout' for r in rows))
out=Path(__file__).with_name('data')/'cases.tsv';out.parent.mkdir(parents=True,exist_ok=True)
out.write_text('# split\tkind\tinput\texpected\tnote\n'+'\n'.join('\t'.join(r) for r in rows)+'\n',encoding='utf-8')
