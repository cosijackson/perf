#include <stdio.h>
#include "cs1300bmp.h"
#include <iostream>
#include <fstream>
#include "Filter.h"

using namespace std;

#include "rtdsc.h"

//
// Forward declare the functions
//
Filter * readFilter(string filename);
double applyFilter(Filter *filter, cs1300bmp *input, cs1300bmp *output);

int
main(int argc, char **argv)
{

	if ( argc < 2) {
		fprintf(stderr, "Usage: %s filter inputfile1 inputfile2 .... \n", argv[0]);
	}

	//
	// Convert to C++ strings to simplify manipulation
	//
	string filtername = argv[1];

	//
	// remove any ".filter" in the filtername
	//
	string filterOutputName = filtername;
	string::size_type loc = filterOutputName.find(".filter");
	if (loc != string::npos) {
		//
		// Remove the ".filter" name, which should occur on all the provided filters
		//
		filterOutputName = filtername.substr(0, loc);
	}

	Filter *filter = readFilter(filtername);

	double sum = 0.0;
	int samples = 0;

	for (int inNum = 2; inNum < argc; inNum++) {
		string inputFilename = argv[inNum];
		string outputFilename = "filtered-" + filterOutputName + "-" + inputFilename;
		struct cs1300bmp *input = new struct cs1300bmp;
		struct cs1300bmp *output = new struct cs1300bmp;
		int ok = cs1300bmp_readfile( (char *) inputFilename.c_str(), input);

		if ( ok ) {
			double sample = applyFilter(filter, input, output);
			sum += sample;
			samples++;
			cs1300bmp_writefile((char *) outputFilename.c_str(), output);
		}
	}
	fprintf(stdout, "Average cycles per sample is %f\n", sum / samples);

}

struct Filter *
readFilter(string filename)
{
	ifstream input(filename.c_str());

	if ( ! input.bad() ) {
		int size = 0;
		input >> size;
		Filter *filter = new Filter(size);
		int div;
		input >> div;
		filter -> setDivisor(div);
		for (int i = 0; i < size; i++) { 				//hard code this - because we know size i
			for (int j = 0; j < size; j++) {
				int value;
				input >> value;
				filter -> set(i, j, value);
			}
		}
		return filter;
	}
}


double
applyFilter(struct Filter *filter, cs1300bmp *input, cs1300bmp *output)
{

	long long cycStart, cycStop;

	cycStart = rdtscll();
	output -> width = input -> width;
	output -> height = input -> height;				//declaring as variables to not be referenced in loops
	int input_width = (input -> width);
	input_width = (input_width) - 1;
	int input_height = input -> height;
	input_height = (input_height) - 1;
	int value;
	int value2;																//declaring value - splitting up int addition into 3 parts, (pipelining, so ea. doesn't have to wait for the one before to finish executing)
	int value3;
	int divisor = filter -> getDivisor(); 		//declare variable instead of having to call func everytime to get value

	int *temp1;
	int *temp2;																//declaring temps which represent splitting up matrix into 3 parts
	int *temp3;

	unsigned char *color1;
	unsigned char *color2;										//declaring variables for splitting up color array into 3 parts
	unsigned char *color3;

	int col1; int col3; int row1; int row3;	 	//declaring col and row offset (col + 1, col - 1)

	int tempFil[9] = {
		filter->get(0, 0),
		filter->get(1, 0),
		filter->get(2, 0),
		filter->get(0, 1),
		filter->get(1, 1),												//flattened 3x3 matrix into 9x1
		filter->get(2, 1),
		filter->get(0, 2),
		filter->get(1, 2),
		filter->get(2, 2)
	};



	for (int col = 1; col < input_width; col++) {
		for (int row = 1; row < input_height; row++) {

			row1 = row - 1;
			col1 = col - 1;											//initializing column and row offsets

			value = 0;														//initializing variables for current color step
			value2 = 0;
			value3 = 0;

			temp1 = &tempFil[0];
			temp2 = temp1+1;
			temp3 = temp1+2;										//extracting next 3 values in array by column

			color1 = &input -> color[0][col1][row1];		//initializing the 3 parts of the color array by column
			color2 = color1 + 1;			//ordered by plane, col, row
			color3 = color1 + 2;



			value = value +  *color1 * (*temp1);
			value2 = value2 +  *(color2) * (*temp2);	//matrix multiplication
			value3 = value3 +  *(color3) * (*temp3);

			temp1 += 3;
			temp2 += 3;																	//shifting to next row of tempFil array
			temp3 += 3;

			color1 += 3;
			color2 += 3;																	//shifting to next row of tempFil array
			color3 += 3;

			value = value +  *color1 * (*temp1);
			value2 = value2 +  *(color2) * (*temp2);	//matrix multiplication
			value3 = value3 +  *(color3) * (*temp3);

			temp1 += 3;
			temp2 += 3;																	//shift
			temp3 += 3;

			color1 += 3;
			color2 += 3;																	//shifting to next row of tempFil array
			color3 += 3;

			value = value +  *color1 * (*temp1);
			value2 = value2 +  *(color2) * (*temp2);	//matrix multiplication
			value3 = value3 +  *(color3) * (*temp3);

			value += value2 + value3;
			value = value / divisor;
			if (value  < 0) { value = 0; }
			if (value  > 255) { value = 255; }
			output -> color[0][col][row] = value; 		//re ordered by plane, col, row

			value = 0;														//initializing variables for current color step
			value2 = 0;
			value3 = 0;

			temp1 = &tempFil[0];
			temp2 = temp1+1;
			temp3 = temp1+2;										//extracting next 3 values in array by column

			color1 = &input -> color[1][col1][row1];		//initializing the 3 parts of the color array by column
			color2 = color1 + 1;			//ordered by plane, col, row
			color3 = color1 + 2;


			value = value +  *color1 * (*temp1);
			value2 = value2 +  *(color2) * (*temp2);	//matrix multiplication
			value3 = value3 +  *(color3) * (*temp3);

			temp1 += 3;
			temp2 += 3;																	//shifting to next row of tempFil array
			temp3 += 3;

			color1 += 3;
			color2 += 3;																	//shifting to next row of tempFil array
			color3 += 3;

			value = value +  *color1 * (*temp1);
			value2 = value2 +  *(color2) * (*temp2);	//matrix multiplication
			value3 = value3 +  *(color3) * (*temp3);

			temp1 += 3;
			temp2 += 3;																	//shift
			temp3 += 3;

			color1 += 3;
			color2 += 3;																	//shifting to next row of tempFil array
			color3 += 3;

			value = value +  *color1 * (*temp1);
			value2 = value2 +  *(color2) * (*temp2);	//matrix multiplication
			value3 = value3 +  *(color3) * (*temp3);
			
			value += value2 + value3;
			value = value / divisor;
			if (value  < 0) { value = 0; }
			if (value  > 255) { value = 255; }
			output -> color[1][col][row] = value;

			value = 0;														//initializing variables for current color step
			value2 = 0;
			value3 = 0;

			temp1 = &tempFil[0];
			temp2 = temp1+1;
			temp3 = temp1+2;										//extracting next 3 values in array by column

			color1 = &input -> color[2][col1][row1];		//initializing the 3 parts of the color array by column
			color2 = color1 + 1;			//ordered by plane, col, row
			color3 = color1 + 2;

			value = value +  *color1 * (*temp1);
			value2 = value2 +  *(color2) * (*temp2);	//matrix multiplication
			value3 = value3 +  *(color3) * (*temp3);

			temp1 += 3;
			temp2 += 3;																	//shifting to next row of tempFil array
			temp3 += 3;

			color1 += 3;
			color2 += 3;																	//shifting to next row of tempFil array
			color3 += 3;

			value = value +  *color1 * (*temp1);
			value2 = value2 +  *(color2) * (*temp2);	//matrix multiplication
			value3 = value3 +  *(color3) * (*temp3);

			temp1 += 3;
			temp2 += 3;																	//shift
			temp3 += 3;

			color1 += 3;
			color2 += 3;																	//shifting to next row of tempFil array
			color3 += 3;

			value = value +  *color1 * (*temp1);
			value2 = value2 +  *(color2) * (*temp2);	//matrix multiplication
			value3 = value3 +  *(color3) * (*temp3);

			value += value2 + value3;
			value = value / divisor;
			if (value  < 0) { value = 0; }
			if (value  > 255) { value = 255; }
			output -> color[2][col][row] = value;



		}
	}

	cycStop = rdtscll();
	double diff = cycStop - cycStart;
	double diffPerPixel = diff / (output -> width * output -> height);
	fprintf(stderr, "Took %f cycles to process, or %f cycles per pixel\n",
	        diff, diff / (output -> width * output -> height));
	return diffPerPixel;
}
