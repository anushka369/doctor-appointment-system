package com.clinic.config;

import com.clinic.dao.DoctorDao;
import com.clinic.dao.PatientDao;
import com.clinic.model.Doctor;
import com.clinic.model.Patient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/** Inserts a few sample rows on first run so the UI is not empty. */
@Component
public class DataSeeder implements CommandLineRunner {

    private final DoctorDao doctorDao;
    private final PatientDao patientDao;

    public DataSeeder(DoctorDao doctorDao, PatientDao patientDao) {
        this.doctorDao = doctorDao;
        this.patientDao = patientDao;
    }

    private static final String[][] DOCTORS = {
            {"Dr. Asha Mehta", "Cardiology", "asha.mehta@clinic.test", "9000000001"},
            {"Dr. Rohan Iyer", "Dermatology", "rohan.iyer@clinic.test", "9000000002"},
            {"Dr. Neha Kapoor", "Pediatrics", "neha.kapoor@clinic.test", "9000000003"},
            {"Dr. Imran Qureshi", "Orthopedics", "imran.q@clinic.test", "9000000004"},
            {"Dr. Priya Venkatesh", "Neurology", "priya.venkatesh@clinic.test", "9000000005"},
            {"Dr. Arjun Malhotra", "General Medicine", "arjun.malhotra@clinic.test", "9000000006"},
            {"Dr. Sunita Rao", "Gynecology", "sunita.rao@clinic.test", "9000000007"},
            {"Dr. Vikram Desai", "ENT", "vikram.desai@clinic.test", "9000000008"},
            {"Dr. Ananya Bose", "Psychiatry", "ananya.bose@clinic.test", "9000000009"},
            {"Dr. Karan Joshi", "Ophthalmology", "karan.joshi@clinic.test", "9000000010"},
            {"Dr. Meera Pillai", "Endocrinology", "meera.pillai@clinic.test", "9000000011"},
            {"Dr. Farhan Sheikh", "Gastroenterology", "farhan.sheikh@clinic.test", "9000000012"},
            {"Dr. Rajesh Khanna", "Urology", "rajesh.khanna@clinic.test", "9000000013"},
            {"Dr. Lakshmi Narayanan", "Pulmonology", "lakshmi.narayanan@clinic.test", "9000000014"},
            {"Dr. Siddharth Menon", "Oncology", "siddharth.menon@clinic.test", "9000000015"},
            {"Dr. Tanvi Kulkarni", "Dentistry", "tanvi.kulkarni@clinic.test", "9000000016"},
            {"Dr. Harish Patel", "Nephrology", "harish.patel@clinic.test", "9000000017"},
            {"Dr. Pooja Agarwal", "Rheumatology", "pooja.agarwal@clinic.test", "9000000018"},
            {"Dr. Nikhil Bhatia", "Radiology", "nikhil.bhatia@clinic.test", "9000000019"},
            {"Dr. Divya Reddy", "Anesthesiology", "divya.reddy@clinic.test", "9000000020"},
            {"Dr. Sandeep Chatterjee", "Cardiology", "sandeep.chatterjee@clinic.test", "9000000021"},
            {"Dr. Fatima Ansari", "Pediatrics", "fatima.ansari@clinic.test", "9000000022"},
            {"Dr. Gaurav Saxena", "Orthopedics", "gaurav.saxena@clinic.test", "9000000023"},
            {"Dr. Isha Banerjee", "Dermatology", "isha.banerjee@clinic.test", "9000000024"},
            {"Dr. Manoj Thakur", "General Surgery", "manoj.thakur@clinic.test", "9000000025"},
            {"Dr. Revathi Subramanian", "Neurology", "revathi.subramanian@clinic.test", "9000000026"},
            {"Dr. Yash Oberoi", "Sports Medicine", "yash.oberoi@clinic.test", "9000000027"},
            {"Dr. Kavita Menon", "Gynecology", "kavita.menon@clinic.test", "9000000028"},
            {"Dr. Aditya Kulkarni", "ENT", "aditya.kulkarni@clinic.test", "9000000029"},
            {"Dr. Shreya Ghosh", "Endocrinology", "shreya.ghosh@clinic.test", "9000000030"},
            {"Dr. Vivek Nambiar", "Psychiatry", "vivek.nambiar@clinic.test", "9000000031"},
            {"Dr. Zoya Khan", "Ophthalmology", "zoya.khan@clinic.test", "9000000032"},
            {"Dr. Pranav Joshi", "Gastroenterology", "pranav.joshi@clinic.test", "9000000033"},
            {"Dr. Madhuri Deshpande", "General Medicine", "madhuri.deshpande@clinic.test", "9000000034"},
            {"Dr. Rahul Verma", "Physiotherapy", "rahul.verma@clinic.test", "9000000035"},
            {"Dr. Sneha Iyer", "Hematology", "sneha.iyer@clinic.test", "9000000036"},
    };

    private static final String[][] PATIENTS = {
            {"Aarav Sharma", "aarav.sharma@example.test", "9000000001"},
            {"Diya Nair", "diya.nair@example.test", "9000000002"},
            {"Kabir Singh", "kabir.singh@example.test", "9000000003"},
            {"Ishaan Gupta", "ishaan.gupta@example.test", "9000000004"},
            {"Ananya Reddy", "ananya.reddy@example.test", "9000000005"},
            {"Vihaan Kapoor", "vihaan.kapoor@example.test", "9000000006"},
            {"Saanvi Joshi", "saanvi.joshi@example.test", "9000000007"},
            {"Arjun Mehta", "arjun.mehta@example.test", "9000000008"},
            {"Myra Choudhury", "myra.choudhury@example.test", "9000000009"},
            {"Reyansh Patel", "reyansh.patel@example.test", "9000000010"},
            {"Aisha Khan", "aisha.khan@example.test", "9000000011"},
            {"Advait Kulkarni", "advait.kulkarni@example.test", "9000000012"},
            {"Kiara Malhotra", "kiara.malhotra@example.test", "9000000013"},
            {"Rohan Das", "rohan.das@example.test", "9000000014"},
            {"Navya Iyer", "navya.iyer@example.test", "9000000015"},
            {"Vivaan Bansal", "vivaan.bansal@example.test", "9000000016"},
            {"Tara Menon", "tara.menon@example.test", "9000000017"},
            {"Dev Agarwal", "dev.agarwal@example.test", "9000000018"},
            {"Riya Bhatt", "riya.bhatt@example.test", "9000000019"},
            {"Aditya Rao", "aditya.rao@example.test", "9000000020"},
            {"Pari Sethi", "pari.sethi@example.test", "9000000021"},
            {"Krish Saxena", "krish.saxena@example.test", "9000000022"},
            {"Anika Verma", "anika.verma@example.test", "9000000023"},
            {"Yuvraj Chauhan", "yuvraj.chauhan@example.test", "9000000024"},
            {"Zara Sheikh", "zara.sheikh@example.test", "9000000025"},
            {"Neil Fernandes", "neil.fernandes@example.test", "9000000026"},
            {"Meghna Pillai", "meghna.pillai@example.test", "9000000027"},
            {"Samar Ahuja", "samar.ahuja@example.test", "9000000028"},
            {"Ira Banerjee", "ira.banerjee@example.test", "9000000029"},
            {"Harsh Vardhan", "harsh.vardhan@example.test", "9000000030"},
            {"Sara D'Souza", "sara.dsouza@example.test", "9000000031"},
            {"Om Prakash", "om.prakash@example.test", "9000000032"},
            {"Tanya Bhargava", "tanya.bhargava@example.test", "9000000033"},
            {"Laksh Thakur", "laksh.thakur@example.test", "9000000034"},
            {"Mahika Sinha", "mahika.sinha@example.test", "9000000035"},
            {"Dhruv Nambiar", "dhruv.nambiar@example.test", "9000000036"},
            {"Esha Mukherjee", "esha.mukherjee@example.test", "9000000037"},
            {"Atharv Jain", "atharv.jain@example.test", "9000000038"},
            {"Kavya Hegde", "kavya.hegde@example.test", "9000000039"},
            {"Anushka Banerjee", "anushka.banerjee@example.test", "9000000041"},
            {"Shashank Singh", "shashank.singh@example.test", "9000000042"},
            {"Swarnava Paul", "swarnava.paul@example.test", "9000000043"},
            {"Rudra Pandey", "rudra.pandey@example.test", "9000000040"},
    };

    /** Adds any sample doctor that is not already present (matched by name); patients likewise. */
    @Override
    public void run(String... args) {
        Set<String> existing = doctorDao.findAll().stream().map(Doctor::name).collect(Collectors.toSet());
        for (String[] d : DOCTORS) {
            if (!existing.contains(d[0])) {
                doctorDao.insert(new Doctor(null, d[0], d[1], d[2], d[3]));
            }
        }
        Set<String> havePatients = patientDao.findAll().stream().map(Patient::name).collect(Collectors.toSet());
        for (String[] p : PATIENTS) {
            if (!havePatients.contains(p[0])) {
                patientDao.insert(new Patient(null, p[0], p[1], p[2]));
            }
        }
    }
}
